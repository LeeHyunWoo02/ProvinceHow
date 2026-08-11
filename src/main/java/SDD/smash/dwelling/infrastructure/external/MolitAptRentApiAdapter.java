package SDD.smash.dwelling.infrastructure.external;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.domain.model.RentRecord;
import SDD.smash.dwelling.domain.port.RentRecordProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import io.micrometer.common.lang.Nullable;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 국토부 아파트 전월세 실거래 API 어댑터. {@code RentRecordProvider} 포트 구현이다.
 * As-Is {@code MolitAptRentAdapter} 를 옮긴 것이며 호출 방식·파싱·예외 처리를 그대로 유지했다.
 *
 * <p>외부 API 어휘({@code LAWD_CD}, {@code DEAL_YMD}, {@code serviceKey})는 이 클래스 밖으로 나가지 않는다.
 * 도메인은 "시군구와 연월로 실거래를 받는다"만 안다.
 */
@Component
@Slf4j
public class MolitAptRentApiAdapter implements RentRecordProvider {

    /** As-Is 호출부가 고정으로 넘기던 값. 페이지네이션은 쓰지 않는다. */
    private static final int PAGE_NO = 1;
    private static final int ROWS = 1000;

    private static final DateTimeFormatter DEAL_YMD_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    public MolitAptRentApiAdapter(RestTemplate restTemplate,
                                  ObjectMapper objectMapper, XmlMapper xmlMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.xmlMapper = xmlMapper;
    }

    @Value("${apis.molit.base-url}")
    private String baseUrl;
    @Value("${apis.molit.path}")
    private String apiPath;
    @Value("${apis.molit.service-key}")
    private String serviceKey;

    @Override
    public List<RentRecord> fetch(SigunguCode code, YearMonth yearMonth) {

        String key = (serviceKey == null) ? "" : serviceKey.trim();
        boolean encodedKey = looksEncoded(key);
        String dealYmd = yearMonth.format(DEAL_YMD_FORMAT);

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .pathSegment(apiPath)
                .queryParam("LAWD_CD", code.value())
                .queryParam("DEAL_YMD", dealYmd)
                .queryParam("pageNo", PAGE_NO)
                .queryParam("numOfRows", ROWS)
                .queryParam("_type", "json");

        final String finalUrl;

        if (encodedKey) {
            String tmp = builder.queryParam("serviceKey", "{sk}")
                    .build(false)
                    .toUriString();
            finalUrl = tmp.replace("{sk}", key);
        } else {
            finalUrl = builder.queryParam("serviceKey", key)
                    .build(true)
                    .toUriString();
        }

        try {
            ResponseEntity<String> resp = restTemplate.getForEntity(finalUrl, String.class);
            String body = resp.getBody();
            JsonNode jsonNode = parseJsonWithXmlFallback(resp.getHeaders().getContentType(), body);
            return extractRecords(jsonNode);
        } catch (Exception e) {
            log.error("[MOLIT] fetch 실패 sigungu={}, ym={}, page={}",
                    code.value(), yearMonth, PAGE_NO, e);
            throw e; // rethrow → 배치의 retry/fault-tolerant 가 동작한다
        }
    }

    private JsonNode parseJsonWithXmlFallback(@Nullable MediaType ct, String body) {
        try {
            if ((ct != null && MediaType.APPLICATION_JSON.includes(ct)) || looksLikeJson(body)) {
                return objectMapper.readTree(body);
            }
            return xmlMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("[MOLIT] 응답 파싱 실패: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private List<RentRecord> extractRecords(JsonNode root) {
        JsonNode items = root.at("/response/body/items/item");
        if (items == null || items.isMissingNode()) return List.of();

        List<RentRecord> list = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                list.add(RentRecordJsonMapper.toRecord(item));
            }
        } else {
            list.add(RentRecordJsonMapper.toRecord(items));
        }
        return list;
    }

    private boolean looksLikeJson(String s) {
        if (s == null) return false;
        String t = s.trim();
        return (t.startsWith("{") && t.endsWith("}")) || (t.startsWith("[") && t.endsWith("]"));
    }

    private boolean looksEncoded(String k) {
        return k.contains("%2B") || k.contains("%2F") || k.contains("%3D");
    }
}
