package SDD.smash.domain.address.application.dto;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;

import java.time.YearMonth;
import java.util.List;

/**
 * 인구 수집 유스케이스의 결과.
 *
 * @param requestedMonth  요청 기준월. 배치의 {@code baseMonth} JobParameter 와 같다
 * @param statisticsMonth 실제로 확보한 자료의 기준월. 아무것도 못 받았으면 {@code null}
 * @param snapshots       적재 대상. {@code sigungu} 테이블에 있는 코드만 남은 상태다
 * @param fetchedCount    공급자가 돌려준 시군구 행 수(대조 전)
 * @param unmatchedCodes  5자리이지만 {@code sigungu} 테이블에 없는 코드. 폐지·통합 행정구역이 여기 걸린다.
 *                        <b>임의 매핑하지 않고 제외</b>하며 건수 파악용으로만 남긴다
 * @param skipped         필수 설정이 없어 외부 호출 자체를 하지 않은 경우 {@code true}
 */
public record PopulationCollectionInfo(
        YearMonth requestedMonth,
        YearMonth statisticsMonth,
        List<PopulationSnapshot> snapshots,
        int fetchedCount,
        List<String> unmatchedCodes,
        boolean skipped) {

    public PopulationCollectionInfo {
        snapshots = (snapshots == null) ? List.of() : List.copyOf(snapshots);
        unmatchedCodes = (unmatchedCodes == null) ? List.of() : List.copyOf(unmatchedCodes);
    }

    /** 설정 부재로 수집을 건너뛴 결과. */
    public static PopulationCollectionInfo skipped(YearMonth requestedMonth) {
        return new PopulationCollectionInfo(requestedMonth, null, List.of(), 0, List.of(), true);
    }

    /** 호출은 했으나 확보한 자료가 없는 결과. */
    public static PopulationCollectionInfo empty(YearMonth requestedMonth) {
        return new PopulationCollectionInfo(requestedMonth, null, List.of(), 0, List.of(), false);
    }

    public int loadedCount() {
        return snapshots.size();
    }

    public int unmatchedCount() {
        return unmatchedCodes.size();
    }
}
