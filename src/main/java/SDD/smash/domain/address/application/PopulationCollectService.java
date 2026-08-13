package SDD.smash.domain.address.application;

import SDD.smash.domain.address.application.dto.PopulationCollectionInfo;
import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.domain.port.PopulationSnapshotProvider;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 외부 통계에서 시군구 인구를 수집하는 유스케이스. 인구 배치의 Reader 가 호출한다.
 *
 * <p>하는 일은 셋이다.
 * <ol>
 *   <li><b>기준월 확정</b> — 요청 기준월 이하의 가장 최근 확정 월로 내려간다(fallback).</li>
 *   <li><b>대조</b> — {@code sigungu} 테이블에 있는 코드만 남긴다. 이것이 전국·시도 합계와
 *       읍면동, 폐지·통합 코드를 걸러내는 1차 방어선이다. <b>명칭 유사도로 매핑하지 않는다.</b></li>
 *   <li><b>구조화 로그</b> — 기준월/건수/소요시간/최종 상태를 한 줄로 남긴다.</li>
 * </ol>
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 이 메서드는 외부 API 를 호출한다.
 * 트랜잭션으로 감싸면 커넥션을 쥔 채 네트워크를 기다리게 된다.
 * DB 조회는 {@link AddressQueryService} 안에서 자기 트랜잭션으로 끝난다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PopulationCollectService {

    /** 로그 식별자. 이 유스케이스를 돌리는 배치 이름과 같다. */
    private static final String BATCH_NAME = "PopulationJob";

    /** 매칭 실패 코드를 로그에 몇 개까지 보여줄지. 전량을 찍지 않는다. */
    private static final int UNMATCHED_SAMPLE_SIZE = 10;

    private final PopulationSnapshotProvider populationSnapshotProvider;
    private final AddressQueryService addressQueryService;

    /**
     * 요청 기준월(yyyyMM) 이하의 최신 확정 자료를 수집한다.
     *
     * <p>같은 기준월로 다시 부르면 같은 결과가 나온다(외부 자료가 그대로인 한).
     * 적재는 upsert 라 재실행해도 행이 늘지 않는다.
     *
     * @param requestedMonth 배치의 {@code baseMonth}. {@code null} 이면 수집하지 않는다
     */
    public PopulationCollectionInfo collect(YearMonth requestedMonth) {

        long startedAt = System.currentTimeMillis();

        if (requestedMonth == null) {
            log.error("[{}] 기준월이 없어 수집을 건너뛴다. baseMonth JobParameter 를 확인할 것", BATCH_NAME);
            return PopulationCollectionInfo.skipped(null);
        }

        if (!populationSnapshotProvider.isAvailable()) {
            log.error("[{}] 인구 통계 공급자 설정이 비어 있어 수집을 건너뛴다. "
                            + "baseMonth={}, status=SKIPPED, reason=MISSING_CONFIG (KOSIS_API_KEY)",
                    BATCH_NAME, requestedMonth);
            return PopulationCollectionInfo.skipped(requestedMonth);
        }

        List<PopulationSnapshot> fetched;
        try {
            fetched = populationSnapshotProvider.fetchLatestNotAfter(requestedMonth);
        } catch (RuntimeException e) {
            log.error("[{}] 인구 수집 실패 baseMonth={}, elapsed={}ms, status=FAILED, reason={}",
                    BATCH_NAME, requestedMonth, elapsed(startedAt), e.getClass().getSimpleName(), e);
            throw e;
        }

        if (fetched.isEmpty()) {
            log.warn("[{}] 확정된 인구 자료를 찾지 못했다. baseMonth={}, elapsed={}ms, status=NO_DATA",
                    BATCH_NAME, requestedMonth, elapsed(startedAt));
            return PopulationCollectionInfo.empty(requestedMonth);
        }

        YearMonth statisticsMonth = fetched.get(0).statisticsMonth();
        Set<SigunguCode> knownCodes = new LinkedHashSet<>(addressQueryService.getAllSigunguCodes());

        List<PopulationSnapshot> matched = new ArrayList<>();
        List<String> unmatchedCodes = new ArrayList<>();
        for (PopulationSnapshot snapshot : fetched) {
            if (knownCodes.contains(snapshot.sigunguCode())) {
                matched.add(snapshot);
            } else {
                unmatchedCodes.add(snapshot.sigunguCode().value());
            }
        }

        if (!unmatchedCodes.isEmpty()) {
            log.warn("[{}] 등록되지 않은 시군구 코드를 제외했다(폐지·통합 추정). "
                            + "baseMonth={}, statisticsMonth={}, unmatched={}, sample={}",
                    BATCH_NAME, requestedMonth, statisticsMonth, unmatchedCodes.size(),
                    unmatchedCodes.subList(0, Math.min(UNMATCHED_SAMPLE_SIZE, unmatchedCodes.size())));
        }

        log.info("[{}] 인구 수집 완료 baseMonth={}, statisticsMonth={}, fetched={}, unmatched={}, "
                        + "loaded={}, elapsed={}ms, status={}",
                BATCH_NAME, requestedMonth, statisticsMonth, fetched.size(), unmatchedCodes.size(),
                matched.size(), elapsed(startedAt), matched.isEmpty() ? "NO_DATA" : "SUCCESS");

        return new PopulationCollectionInfo(
                requestedMonth, statisticsMonth, matched, fetched.size(), unmatchedCodes, false);
    }

    private long elapsed(long startedAt) {
        return System.currentTimeMillis() - startedAt;
    }
}
