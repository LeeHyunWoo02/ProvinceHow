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
     * 건물명·지번은 유형마다 다르다(docs/external-api-spec.md 4.4). 단독다가구는 두 필드가 아예 없어 null 이 되며,
     * 전월세 판정은 {@code deposit}/{@code monthlyRent} 만 쓰므로 집계에는 영향이 없다.
     */
    static RentRecord toRecord(HousingType housingType, JsonNode node) {
        String buildingName = switch (housingType) {
            case APARTMENT -> text(node, "aptNm", "아파트");
            case MULTIPLEX_HOUSE -> text(node, "mhouseNm", "연립다세대");
            case DETACHED_HOUSE -> null;
        };
        String jibun = (housingType == HousingType.DETACHED_HOUSE) ? null : text(node, "jibun", "지번");
        Integer deposit = num(node, "deposit", "보증금액");
        Integer monthly = num(node, "monthlyRent", "월세금액");

        return new RentRecord(buildingName, jibun, nullZero(deposit), nullZero(monthly));
    }
}
