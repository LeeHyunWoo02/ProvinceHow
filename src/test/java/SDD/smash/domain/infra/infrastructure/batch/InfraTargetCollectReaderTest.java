package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.port.InfraFacilityProvider;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraCollectTarget;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraTargetResult;
import SDD.smash.domain.infra.infrastructure.external.LocalDataApiException;
import SDD.smash.domain.infra.infrastructure.external.LocalDataCallBudgetExceededException;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 수집 Reader. 외부 호출은 가짜 공급자로 대체하고 Spring 없이 돈다.
 */
class InfraTargetCollectReaderTest {

    private static final String RUN_KEY = "2026-08-19";
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 19);
    private static final int STALL_THRESHOLD_DAYS = 7;
    private static final IndustryCode CAFE = IndustryCode.of("CAFE");
    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");
    private static final LocalDataRegionCode SUWON = LocalDataRegionCode.of("3740000");
    private static final LocalDataRegionCode BUSAN = LocalDataRegionCode.of("3130000");

    private final Map<LocalDataRegionCode, SigunguCode> index = Map.of(
            JONGNO, SigunguCode.of("11110"),
            SUWON, SigunguCode.of("41110"),
            BUSAN, SigunguCode.of("26110"));

    @Test
    @DisplayName("예산이 소진되면 예외 없이 스트림을 끝내고 그때까지 수집분은 남는다")
    void endsStreamWithoutFailureWhenBudgetIsExhausted() {
        // given — 두 대상만 받을 수 있는 예산
        FakeProvider provider = new FakeProvider(2);
        InfraTargetCollectReader reader = reader(provider, targets(JONGNO, SUWON, BUSAN));

        // when
        List<InfraTargetResult> read = readAll(reader);

        // then
        assertThat(read).hasSize(2);
        assertThat(reader.budgetExhausted()).isTrue();
        assertThat(reader.collectedTargets()).isEqualTo(2);
        assertThat(provider.requested).containsExactly(JONGNO, SUWON);
    }

    @Test
    @DisplayName("대상 처리 중간에 예산이 끊기면 그 대상은 결과를 만들지 않아 미완료로 남는다")
    void doesNotStageTargetWhenBudgetRunsOutMidTarget() {
        // given — 첫 대상은 성공, 두 번째 대상 호출 도중 예산이 끊긴다
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        provider.failures.put(SUWON, new LocalDataCallBudgetExceededException("[localdata] 일일 호출 예산 소진"));
        InfraTargetCollectReader reader = reader(provider, targets(JONGNO, SUWON, BUSAN));

        // when
        List<InfraTargetResult> read = readAll(reader);

        // then — 부분 페이지가 저장되지 않는다. 부산은 아예 시도하지 않는다
        assertThat(read).hasSize(1);
        assertThat(read.get(0).target().regionCodeValue()).isEqualTo(JONGNO.value());
        assertThat(reader.budgetExhausted()).isTrue();
        assertThat(provider.requested).containsExactly(JONGNO, SUWON);
    }

    @Test
    @DisplayName("이미 수집한 대상은 남은 대상 목록에 없으므로 다시 호출하지 않는다")
    void doesNotCallAlreadyCollectedTargets() {
        // given — 다음 실행이 넘겨받은 것은 미수집 대상뿐이다
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        InfraTargetCollectReader reader = reader(provider, targets(BUSAN));

        // when
        readAll(reader);

        // then
        assertThat(provider.requested).containsExactly(BUSAN);
    }

    @Test
    @DisplayName("일시적 오류로 실패한 대상은 실행 내 2차 패스에서 한 번 더 시도한다")
    void retriesTransientFailureInSecondPass() {
        // given — 종로는 첫 호출만 실패한다
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        provider.transientFailures.put(JONGNO, 1);
        InfraTargetCollectReader reader = reader(provider, targets(JONGNO, SUWON));

        // when
        List<InfraTargetResult> read = readAll(reader);

        // then — 1차에서 실패한 종로가 2차 패스에서 성공한다
        assertThat(read).hasSize(2);
        assertThat(reader.unresolvedTargets()).isZero();
        assertThat(provider.requested).containsExactly(JONGNO, SUWON, JONGNO);
    }

    @Test
    @DisplayName("2차 패스에서도 실패하면 Step 을 실패시키지 않고 그 대상만 미완료로 남긴다")
    void leavesTargetUncollectedWhenSecondPassAlsoFails() {
        // given
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        provider.transientFailures.put(JONGNO, 99);
        InfraTargetCollectReader reader = reader(provider, targets(JONGNO, SUWON));

        // when
        List<InfraTargetResult> read = readAll(reader);

        // then
        assertThat(read).hasSize(1);
        assertThat(reader.unresolvedTargets()).isEqualTo(1);
    }

    @Test
    @DisplayName("결과가 0건인 대상도 수집 완료로 기록해 회차 완성을 셀 수 있게 한다")
    void emitsResultForTargetWithNoFacility() {
        // given
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        provider.empty.add(BUSAN);
        InfraTargetCollectReader reader = reader(provider, targets(BUSAN));

        // when
        List<InfraTargetResult> read = readAll(reader);

        // then
        assertThat(read).hasSize(1);
        assertThat(read.get(0).counts()).isEmpty();
        assertThat(read.get(0).facilityCount()).isZero();
    }

    @Test
    @DisplayName("Writer 는 Reader 가 정한 회차 키로 저장한다")
    void exposesRunKeyForWriter() {
        assertThat(reader(new FakeProvider(1), List.of()).runKey()).isEqualTo(RUN_KEY);
    }

    @Test
    @DisplayName("회차 시작일로부터 임계 일수를 넘도록 대상이 남으면 stall 로 판정한다")
    void reportsStallWhenRunExceedsThreshold() {
        // given — 2026-08-01 에 시작한 회차인데 오늘이 2026-08-19 다(18일 경과)
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        provider.transientFailures.put(JONGNO, 99);
        InfraTargetCollectReader reader =
                reader(provider, targets(JONGNO, SUWON), "2026-08-01", TODAY);

        // when
        readAll(reader);

        // then — 영구 실패 대상이 남아 회차가 완성되지 않는다
        assertThat(reader.runStalled()).isTrue();
        assertThat(reader.unresolvedSamples()).containsExactly(JONGNO.value() + "/CAFE");
    }

    @Test
    @DisplayName("예산 때문에 며칠 걸리는 것은 stall 이 아니다")
    void doesNotReportStallWithinThreshold() {
        // given — 어제 시작한 회차. 예산이 없어 한 건도 못 받았다
        FakeProvider provider = new FakeProvider(0);
        InfraTargetCollectReader reader =
                reader(provider, targets(JONGNO, SUWON), "2026-08-18", TODAY);

        // when
        readAll(reader);

        // then
        assertThat(reader.budgetExhausted()).isTrue();
        assertThat(reader.runStalled()).isFalse();
    }

    @Test
    @DisplayName("남은 대상이 없으면 회차가 오래됐어도 stall 이 아니다")
    void doesNotReportStallWhenEveryTargetIsCollected() {
        FakeProvider provider = new FakeProvider(Integer.MAX_VALUE);
        InfraTargetCollectReader reader =
                reader(provider, targets(JONGNO, SUWON), "2026-01-01", TODAY);

        readAll(reader);

        assertThat(reader.runStalled()).isFalse();
    }

    // ------------------------------------------------------------------ 픽스처

    private InfraTargetCollectReader reader(InfraFacilityProvider provider, List<InfraCollectTarget> targets) {
        return reader(provider, targets, RUN_KEY, TODAY);
    }

    private InfraTargetCollectReader reader(InfraFacilityProvider provider, List<InfraCollectTarget> targets,
                                            String runKey, LocalDate today) {
        return new InfraTargetCollectReader(runKey, provider, targets, index, Map.of(),
                today, STALL_THRESHOLD_DAYS);
    }

    private static List<InfraCollectTarget> targets(LocalDataRegionCode... regions) {
        List<InfraCollectTarget> targets = new ArrayList<>();
        for (LocalDataRegionCode region : regions) {
            targets.add(new InfraCollectTarget(CAFE, region));
        }
        return targets;
    }

    private static List<InfraTargetResult> readAll(InfraTargetCollectReader reader) {
        List<InfraTargetResult> results = new ArrayList<>();
        InfraTargetResult item;
        while ((item = reader.read()) != null) {
            results.add(item);
        }
        return results;
    }

    /** 호출 예산·일시적 오류를 흉내 내는 공급자. */
    private static final class FakeProvider implements InfraFacilityProvider {

        private final int capacity;
        private final List<LocalDataRegionCode> requested = new ArrayList<>();
        private final Map<LocalDataRegionCode, RuntimeException> failures = new LinkedHashMap<>();
        private final Map<LocalDataRegionCode, Integer> transientFailures = new LinkedHashMap<>();
        private final List<LocalDataRegionCode> empty = new ArrayList<>();
        private int used;

        private FakeProvider(int capacity) {
            this.capacity = capacity;
        }

        @Override
        public FacilityCollection collect(IndustryCode industryCode, LocalDataRegionCode regionCode) {
            requested.add(regionCode);
            used++;

            RuntimeException fixed = failures.get(regionCode);
            if (fixed != null) {
                throw fixed;
            }
            Integer remainingFailures = transientFailures.get(regionCode);
            if (remainingFailures != null && remainingFailures > 0) {
                transientFailures.put(regionCode, remainingFailures - 1);
                throw new LocalDataApiException("[localdata] 읽기 타임아웃");
            }
            if (empty.contains(regionCode)) {
                return FacilityCollection.empty(1);
            }
            return FacilityCollection.of(List.of(
                    new InfraFacility("MNG-" + regionCode.value(), BusinessStatus.OPERATING, regionCode)), 1);
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public boolean hasRemainingCapacity() {
            return used < capacity;
        }

        @Override
        public String readinessDescription() {
            return "fake";
        }
    }
}
