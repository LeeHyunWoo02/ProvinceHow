package SDD.smash.Dwelling.Adapter;

import SDD.smash.Dwelling.Dto.RentRecord;
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

import static SDD.smash.Dwelling.Converter.RentRecordConverter.toRecord;

@Component
@Slf4j
public class MolitAptRentAdapter {
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    public MolitAptRentAdapter(RestTemplate restTemplate,
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

    /**
     * 월별 아파트 전월세 자료 조회
     * */
    public List<RentRecord> fetchMonth(String sigunguCode, YearMonth yearMonth,
                                       int pageNo, int rows){

        String key = (serviceKey == null) ? "" : serviceKey.trim();
        boolean encodedKey = looksEncoded(key);
        String dealYmd = yearMonth.format(DateTimeFormatter.ofPattern("yyyyMM"));

        UriComponentsBuilder builder = UriComponentsBuilder
                .fromHttpUrl(baseUrl)
                .pathSegment(apiPath)
                .queryParam("LAWD_CD", sigunguCode)
                .queryParam("DEAL_YMD", dealYmd)
                .queryParam("pageNo", pageNo)
                .queryParam("numOfRows", rows)
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
        try{
            ResponseEntity<String> resp = restTemplate.getForEntity(finalUrl, String.class); // api 호출
            String body = resp.getBody();
            JsonNode jsonNode = parseJsonWithXmlFallback(resp.getHeaders().getContentType(), body);
            return extractRecords(jsonNode);
        } catch (Exception e){
            log.error("[API ERROR] sigungu={}, ym={}, page={}",
                    sigunguCode, yearMonth, pageNo, e);
            throw e; // rethrow → 배치 retry/fault-tolerant가 동작
        }

    }

    private JsonNode parseJsonWithXmlFallback(@Nullable MediaType ct, String body) {
        try {
            if ((ct != null && MediaType.APPLICATION_JSON.includes(ct)) || looksLikeJson(body)) {
                return objectMapper.readTree(body);
            }
            return xmlMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            log.warn("parseJsonWithXmlFallback failed: {}", e.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    /**
     * aptNm, jibum, deposit, monthlyRent 만 추출
     * */
    private List<RentRecord> extractRecords(JsonNode root) {
        JsonNode items = root.at("/response/body/items/item");
        if (items == null || items.isMissingNode()) return List.of();

        List<RentRecord> list = new ArrayList<>();
        if (items.isArray()) {
            for (JsonNode item : items) {
                RentRecord record = toRecord(item);
                list.add(record);
            }
        } else {
            list.add(toRecord(items));
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
