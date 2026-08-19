package SDD.smash.domain.infra.infrastructure.persistence;

import SDD.smash.IntegrationTestSupport;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.RegionIndustryCount;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore.StagedTarget;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore.TargetKey;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * staging 체크포인트의 합산 upsert. 실제 MySQL 이 필요하다({@code ON DUPLICATE KEY UPDATE}).
 */
class InfraCollectionStagingStoreTest extends IntegrationTestSupport {

    private static final String RUN_KEY = "2026-08-19-test";
    private static final SigunguCode SUWON_JANGAN = SigunguCode.of("41111");
    private static final SigunguCode SUWON_PALDAL = SigunguCode.of("41115");
    private static final String CAFE = "CAFE";

    @Autowired
    private InfraCollectionStagingStore stagingStore;

    /** 청크 트랜잭션과 <b>같은</b> 매니저다. 원자성 검증이 실제 배치 경로와 같은 조건이어야 한다. */
    @Autowired
    @Qualifier("dataTransactionManager")
    private PlatformTransactionManager dataTransactionManager;

    @BeforeEach
    void clean() {
        stagingStore.purge(RUN_KEY);
    }

    /** 테스트 행을 컨테이너 DB 에 남기지 않는다 — {@code runKeys()} 를 보는 테스트가 오염된다. */
    @AfterEach
    void purge() {
        stagingStore.purge(RUN_KEY);
    }

    @Test
    @DisplayName("같은 시군구·업종에 여러 대상이 기여하면 합산된다")
    void sumsCountsContributedByMultipleTargets() {
        // given — 두 인허가기관이 같은 시군구·업종에 기여한다
        stagingStore.save(RUN_KEY, List.of(
                target("3740000", Map.of(SUWON_JANGAN, 3, SUWON_PALDAL, 2)),
                target("3750000", Map.of(SUWON_JANGAN, 4))));

        // when
        List<RegionIndustryCount> counts = stagingStore.counts(RUN_KEY);

        // then
        assertThat(counts).containsExactlyInAnyOrder(
                new RegionIndustryCount(SUWON_JANGAN, IndustryCode.of(CAFE), 7),
                new RegionIndustryCount(SUWON_PALDAL, IndustryCode.of(CAFE), 2));
    }

    @Test
    @DisplayName("이미 수집한 대상은 완료 목록에 남아 다시 수집되지 않으므로 이중 합산이 없다")
    void doesNotDoubleCountOnResume() {
        // given — 첫 실행에서 한 대상만 수집했다
        stagingStore.save(RUN_KEY, List.of(target("3740000", Map.of(SUWON_JANGAN, 3))));

        // when — 다음 실행은 완료 목록을 보고 남은 대상만 저장한다
        java.util.Set<TargetKey> completed = stagingStore.completedTargets(RUN_KEY);
        assertThat(completed).containsExactly(new TargetKey("3740000", CAFE));

        stagingStore.save(RUN_KEY, List.of(target("3750000", Map.of(SUWON_JANGAN, 4))));

        // then — 3 이 두 번 더해지지 않는다
        assertThat(stagingStore.counts(RUN_KEY)).containsExactly(
                new RegionIndustryCount(SUWON_JANGAN, IndustryCode.of(CAFE), 7));
    }

    @Test
    @DisplayName("결과가 0건인 대상도 완료로 기록해 회차 완성을 셀 수 있다")
    void recordsTargetWithoutAnyFacility() {
        stagingStore.save(RUN_KEY, List.of(target("3130000", Map.of())));

        assertThat(stagingStore.completedTargets(RUN_KEY)).containsExactly(new TargetKey("3130000", CAFE));
        assertThat(stagingStore.counts(RUN_KEY)).isEmpty();
    }

    @Test
    @DisplayName("반영이 끝난 회차를 지우면 다음 실행은 새 회차로 시작한다")
    void purgeRemovesBothTablesForRun() {
        stagingStore.save(RUN_KEY, List.of(target("3740000", Map.of(SUWON_JANGAN, 3))));

        stagingStore.purge(RUN_KEY);

        assertThat(stagingStore.completedTargets(RUN_KEY)).isEmpty();
        assertThat(stagingStore.counts(RUN_KEY)).isEmpty();
        assertThat(stagingStore.runKeys()).doesNotContain(RUN_KEY);
    }

    @Test
    @DisplayName("트랜잭션이 롤백되면 카운트와 대상 진행이 둘 다 사라진다 - 카운트만 남으면 이중 합산이 된다")
    void rollsBackCountsAndTargetProgressTogether() {
        // given — 청크 트랜잭션과 같은 매니저로 트랜잭션을 연다
        TransactionTemplate transactionTemplate = new TransactionTemplate(dataTransactionManager);

        // when — save() 안의 두 batchUpdate 가 끝난 뒤 강제로 롤백한다
        transactionTemplate.execute(status -> {
            stagingStore.save(RUN_KEY, List.of(target("3740000", Map.of(SUWON_JANGAN, 3))));
            status.setRollbackOnly();
            return null;
        });

        // then — 둘 다 비어야 한다. 하나라도 남으면 각각 autocommit 됐다는 뜻이다
        assertThat(stagingStore.completedTargets(RUN_KEY)).isEmpty();
        assertThat(stagingStore.counts(RUN_KEY)).isEmpty();
    }

    @Test
    @DisplayName("트랜잭션이 커밋되면 카운트와 대상 진행이 함께 남는다")
    void commitsCountsAndTargetProgressTogether() {
        TransactionTemplate transactionTemplate = new TransactionTemplate(dataTransactionManager);

        transactionTemplate.execute(status -> {
            stagingStore.save(RUN_KEY, List.of(target("3740000", Map.of(SUWON_JANGAN, 3))));
            return null;
        });

        assertThat(stagingStore.completedTargets(RUN_KEY)).containsExactly(new TargetKey("3740000", CAFE));
        assertThat(stagingStore.counts(RUN_KEY)).containsExactly(
                new RegionIndustryCount(SUWON_JANGAN, IndustryCode.of(CAFE), 3));
    }

    private static StagedTarget target(String openOrgCode, Map<SigunguCode, Integer> counts) {
        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        return new StagedTarget(openOrgCode, CAFE, counts, total, 1);
    }
}
