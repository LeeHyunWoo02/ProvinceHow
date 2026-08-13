package SDD.smash.domain.dwelling.infrastructure.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;

/**
 * 국토부 실거래 API 응답 한 페이지를 읽는 얇은 래퍼.
 *
 * <p><b>"빈 응답"과 "실제 0건"을 가르는 판정이 여기 있다.</b> 실측으로 확인된 응답 형태는 셋이다.
 * <ul>
 *   <li>정상: {@code response.header.resultCode="000"} + {@code response.body.totalCount=N}.
 *       존재하지 않는 {@code LAWD_CD} 도 <b>오류가 아니라</b> {@code resultCode=000, totalCount=0,
 *       items=""} 로 온다 → <b>확정 0건</b></li>
 *   <li>게이트웨이 오류: HTTP 403 + {@code OpenAPI_ServiceResponse.cmmMsgHeader.errMsg}
 *       (미등록 키, 일일 한도 초과 등) → <b>판정 불가</b></li>
 *   <li>본문이 없거나 {@code totalCount} 가 없음 → 0건인지 알 수 없다 → <b>판정 불가</b></li>
 * </ul>
 * 즉 <b>{@code totalCount} 가 이 판정의 유일한 근거</b>다. 항목 배열이 비었다는 사실만으로는
 * 0건인지 유실인지 알 수 없다.
 *
 * <p>{@code _type=json} 이 아닌 XML 로 떨어질 때는 {@code XmlMapper} 가 루트 엘리먼트({@code <response>})를
 * 벗겨내므로 경로가 한 단계 짧아진다. 두 경로를 모두 본다.
 */
final class MolitApiResponse {

    private static final String GATEWAY_ERROR_FIELD = "OpenAPI_ServiceResponse";

    private final JsonNode root;

    private MolitApiResponse(JsonNode root) {
        this.root = (root == null) ? MissingNode.getInstance() : root;
    }

    static MolitApiResponse of(JsonNode root) {
        return new MolitApiResponse(root);
    }

    /** 공공데이터포털 게이트웨이가 막았을 때의 사유. 없으면 {@code null}. 인증키는 담기지 않는다. */
    String gatewayError() {
        JsonNode header = root.at("/" + GATEWAY_ERROR_FIELD + "/cmmMsgHeader");
        if (header.isMissingNode()) {
            header = root.at("/cmmMsgHeader");
        }
        if (header.isMissingNode()) {
            return null;
        }
        String errMsg = header.path("errMsg").asText("");
        String reasonCode = header.path("returnReasonCode").asText("");
        return errMsg + (reasonCode.isEmpty() ? "" : " (code=" + reasonCode + ")");
    }

    /** {@code 000} 처럼 0 으로만 이뤄진 코드가 성공이다. 기관에 따라 {@code 00} 으로도 온다. */
    boolean isSuccess() {
        String code = resultCode();
        return code != null && !code.isEmpty() && code.chars().allMatch(c -> c == '0');
    }

    String resultCode() {
        return firstText("/response/header/resultCode", "/header/resultCode");
    }

    String resultMsg() {
        return firstText("/response/header/resultMsg", "/header/resultMsg");
    }

    /** 기관이 보고한 총건수. 알 수 없으면 {@code null} — 이때는 0건이라고 단정하면 안 된다. */
    Integer totalCount() {
        String raw = firstText("/response/body/totalCount", "/body/totalCount");
        if (raw == null) {
            return null;
        }
        String digits = raw.replaceAll("[^0-9-]", "");
        if (digits.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** 항목 노드. 단일 객체·배열·빈 문자열({@code ""}) 어느 쪽으로도 온다. */
    JsonNode items() {
        JsonNode items = root.at("/response/body/items/item");
        if (items.isMissingNode()) {
            items = root.at("/body/items/item");
        }
        return items;
    }

    private String firstText(String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (!node.isMissingNode() && !node.isNull()) {
                String text = node.asText("").trim();
                if (!text.isEmpty()) {
                    return text;
                }
            }
        }
        return null;
    }
}
