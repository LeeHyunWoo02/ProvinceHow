package SDD.smash.domain.dwelling.infrastructure.external;

import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import com.fasterxml.jackson.databind.JsonNode;

import static SDD.smash.global.util.BatchTextUtil.nullZero;
import static SDD.smash.global.util.MapperUtil.num;
import static SDD.smash.global.util.MapperUtil.text;

/**
 * 국토부 응답 JSON → 도메인 {@code RentRecord} 변환.
 * As-Is {@code RentRecordConverter} 를 옮긴 것이다.
 *
 * <p>외부 API 어휘({@code aptNm}, {@code 보증금액}, {@code 월세금액})는 여기까지만 존재한다.
 * 값이 없으면 0 으로 채우는 것도 As-Is 그대로다 — 월세 0 이 곧 "전세"라는 판정 기준이 된다.
 */
final class RentRecordJsonMapper {

    private RentRecordJsonMapper() {
    }

    /**
     * @param housingType 유형별 필드 분기는 다음 Phase 에서 채운다. 지금은 아파트 후보 필드로 공통 처리한다
     */
    static RentRecord toRecord(HousingType housingType, JsonNode node) {
        String buildingName = text(node, "aptNm", "아파트");
        String jibun = text(node, "jibun", "지번");
        Integer deposit = num(node, "deposit", "보증금액");
        Integer monthly = num(node, "monthlyRent", "월세금액");

        return new RentRecord(buildingName, jibun, nullZero(deposit), nullZero(monthly));
    }
}
