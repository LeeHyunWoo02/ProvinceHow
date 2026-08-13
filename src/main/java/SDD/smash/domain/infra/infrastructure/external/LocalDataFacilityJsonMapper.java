package SDD.smash.domain.infra.infrastructure.external;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Iterator;
import java.util.Map;

/**
 * 인허가 API 응답 항목 → 도메인 사업장 변환.
 *
 * <p>외부 어휘({@code MNG_NO}, {@code SALS_STTS_CD}, {@code OPN_ATMY_GRP_CD})는 이 클래스 밖으로
 * 나가지 않는다.
 *
 * <p><b>필드 집합은 업종마다 다르다.</b> 전 업종 공통으로 신뢰할 수 있는 것은
 * 관리번호 / 영업상태 / 개방자치단체코드 뿐이라 그 셋만 읽는다. 키 이름이 대소문자로 흔들리는
 * 업종이 있을 수 있어 정확 일치 → 대소문자 무시 순으로 찾는다.
 */
public final class LocalDataFacilityJsonMapper {

    static final String FIELD_MANAGEMENT_NO = "MNG_NO";
    static final String FIELD_STATUS = "SALS_STTS_CD";
    static final String FIELD_ORG_CODE = "OPN_ATMY_GRP_CD";

    private LocalDataFacilityJsonMapper() {
    }

    /**
     * @param fallbackOrgCode 응답에 개방자치단체코드가 없을 때 쓸 요청 기준 코드
     * @return 관리번호가 없으면 {@code null} — 중복 제거 키가 없으면 셀 수 없다
     */
    public static InfraFacility toFacility(JsonNode item, LocalDataRegionCode fallbackOrgCode) {
        if (item == null || !item.isObject()) {
            return null;
        }
        String managementNo = text(item, FIELD_MANAGEMENT_NO);
        if (managementNo == null) {
            return null;
        }

        BusinessStatus status = BusinessStatus.fromCode(text(item, FIELD_STATUS));

        LocalDataRegionCode orgCode = fallbackOrgCode;
        String rawOrgCode = text(item, FIELD_ORG_CODE);
        if (rawOrgCode != null) {
            try {
                orgCode = LocalDataRegionCode.of(rawOrgCode);
            } catch (RuntimeException e) {
                orgCode = fallbackOrgCode;
            }
        }

        return new InfraFacility(managementNo, status, orgCode);
    }

    private static String text(JsonNode item, String field) {
        JsonNode node = item.get(field);
        if (node == null || node.isNull()) {
            node = findIgnoreCase(item, field);
        }
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText(null);
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static JsonNode findIgnoreCase(JsonNode item, String field) {
        Iterator<Map.Entry<String, JsonNode>> fields = item.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            if (entry.getKey().equalsIgnoreCase(field)) {
                return entry.getValue();
            }
        }
        return null;
    }
}
