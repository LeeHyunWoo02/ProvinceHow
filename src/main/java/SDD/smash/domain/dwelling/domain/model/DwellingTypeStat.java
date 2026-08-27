package SDD.smash.domain.dwelling.domain.model;

/**
 * 주택유형별 전월세 시세 (조회 전용 모델).
 *
 * <p>Aggregate 가 아니다. 주거 점수 계산은 3종을 풀링한 통합 평균({@link DwellingMarket})만 쓰므로
 * 유형별 세분화를 {@code DwellingMarket} 에 넣지 않고 별도 조회 모델로 분리했다.
 */
public record DwellingTypeStat(HousingType housingType, RentStat monthly, RentStat jeonse) {
}
