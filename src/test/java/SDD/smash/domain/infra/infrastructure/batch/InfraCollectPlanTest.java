package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraCollectTarget;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore.TargetKey;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 회차와 남은 대상 계산. DB 없이 도는 순수 테스트다.
 */
class InfraCollectPlanTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);

    @Test
    @DisplayName("staging 이 비어 있으면 오늘 날짜로 새 회차를 연다")
    void startsNewRunWhenStagingIsEmpty() {
        assertThat(InfraCollectPlan.runKey(List.of(), TODAY)).isEqualTo("2026-08-19");
    }

    @Test
    @DisplayName("미완성 회차가 남아 있으면 날짜가 바뀌어도 그 회차를 이어받는다")
    void resumesOldestExistingRun() {
        String runKey = InfraCollectPlan.runKey(List.of("2026-08-17", "2026-08-18"), TODAY);

        assertThat(runKey).isEqualTo("2026-08-17");
    }

    @Test
    @DisplayName("회차가 둘 이상 남아 있어도 가장 오래된 하나만 이어받는다")
    void picksOnlyTheOldestWhenSeveralRunsRemain() {
        // given — 정리 실패나 수동 개입으로 회차가 셋 남았다
        List<String> remaining = List.of("2026-08-10", "2026-08-15", "2026-08-18");

        // when
        String runKey = InfraCollectPlan.runKey(remaining, TODAY);

        // then — 정책은 "가장 오래된 것 하나". 나머지는 로그로만 드러낸다
        assertThat(runKey).isEqualTo("2026-08-10");
    }

    @Test
    @DisplayName("기대 대상은 지역 매핑 × 활성 업종이다")
    void buildsTargetsAsRegionsTimesIndustries() {
        List<InfraCollectTarget> targets = InfraCollectPlan.allTargets(mapping(), industries());

        assertThat(targets).hasSize(4);
        assertThat(targets).extracting(InfraCollectTarget::regionCodeValue)
                .containsExactly("3000000", "3000000", "3740000", "3740000");
    }

    @Test
    @DisplayName("이미 수집한 대상은 다시 호출하지 않는다")
    void skipsAlreadyCollectedTargets() {
        List<InfraCollectTarget> all = InfraCollectPlan.allTargets(mapping(), industries());
        Set<TargetKey> completed = Set.of(new TargetKey("3000000", "CAFE"));

        List<InfraCollectTarget> pending = InfraCollectPlan.pending(all, completed);

        assertThat(pending).hasSize(3);
        assertThat(pending).noneMatch(target ->
                target.regionCodeValue().equals("3000000") && target.industryCodeValue().equals("CAFE"));
        assertThat(InfraCollectPlan.collectedCount(all, completed)).isEqualTo(1);
    }

    @Test
    @DisplayName("매핑에 같은 개방자치단체코드가 두 번 있어도 대상은 한 번만 만든다")
    void deduplicatesTargetsWhenMappingHasDuplicateOrgCode() {
        // given — 매핑 한 줄이 잘못 복제된 상황. 카운트가 합산 upsert 라 두 배가 될 수 있다
        RegionCodeMapping duplicated = new RegionCodeMapping(List.of(
                new RegionCodeMapping.Entry(LocalDataRegionCode.of("3000000"), SigunguCode.of("11110"), "종로구"),
                new RegionCodeMapping.Entry(LocalDataRegionCode.of("3000000"), SigunguCode.of("11110"), "종로구(중복)"),
                new RegionCodeMapping.Entry(LocalDataRegionCode.of("3740000"), SigunguCode.of("41110"), "수원시")));

        // when
        List<InfraCollectTarget> targets = InfraCollectPlan.allTargets(duplicated, industries());

        // then — 2지역 × 2업종 = 4건이고 중복 대상이 없다
        assertThat(targets).hasSize(4);
        assertThat(targets).doesNotHaveDuplicates();
        assertThat(InfraCollectPlan.pending(targets, Set.of())).hasSize(4);
    }

    @Test
    @DisplayName("기대 대상이 전부 채워졌을 때만 회차가 완성이다")
    void isCompleteOnlyWhenEveryTargetIsCollected() {
        List<InfraCollectTarget> all = InfraCollectPlan.allTargets(mapping(), industries());

        assertThat(InfraCollectPlan.isComplete(all, Set.of(new TargetKey("3000000", "CAFE")))).isFalse();
        assertThat(InfraCollectPlan.isComplete(all, Set.of(
                new TargetKey("3000000", "CAFE"), new TargetKey("3000000", "GYM"),
                new TargetKey("3740000", "CAFE"), new TargetKey("3740000", "GYM")))).isTrue();
    }

    @Test
    @DisplayName("활성 업종이 늘어나면 이미 완성이던 회차가 다시 미완성이 된다")
    void becomesIncompleteWhenNewIndustryIsActivated() {
        Set<TargetKey> completed = Set.of(
                new TargetKey("3000000", "CAFE"), new TargetKey("3000000", "GYM"),
                new TargetKey("3740000", "CAFE"), new TargetKey("3740000", "GYM"));

        List<IndustryMasterEntry> grown = List.of(industry("CAFE"), industry("GYM"), industry("MART"));

        assertThat(InfraCollectPlan.isComplete(InfraCollectPlan.allTargets(mapping(), grown), completed))
                .isFalse();
    }

    // ------------------------------------------------------------------ 픽스처

    private static RegionCodeMapping mapping() {
        return new RegionCodeMapping(List.of(
                new RegionCodeMapping.Entry(LocalDataRegionCode.of("3000000"), SigunguCode.of("11110"), "종로구"),
                new RegionCodeMapping.Entry(LocalDataRegionCode.of("3740000"), SigunguCode.of("41110"), "수원시")));
    }

    private static List<IndustryMasterEntry> industries() {
        return List.of(industry("CAFE"), industry("GYM"));
    }

    private static IndustryMasterEntry industry(String code) {
        return new IndustryMasterEntry(IndustryCode.of(code), code, Major.LIFE, "slug-" + code,
                "dataset-" + code, true, true, null);
    }
}
