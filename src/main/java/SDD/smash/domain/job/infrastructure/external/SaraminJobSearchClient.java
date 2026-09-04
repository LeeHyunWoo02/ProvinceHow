package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.global.metrics.ExternalApiMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 사람인 채용검색 오픈 API 호출 공통부. 집계/카드/프로필 세 어댑터가 URI 조립·호출·access-key
 * 마스킹·성공실패 메트릭을 여기로 위임한다. 파싱과 도메인 변환은 각 어댑터가 맡는다.
 *
 * <p>엔드포인트/파라미터/마스킹 규칙은 어댑터에 흩어져 있던 것과 동일하다.
 */
@Component
@Slf4j
public class SaraminJobSearchClient {

    /** 메트릭의 api 태그 값. 어댑터가 아니라 수집원 단위다(일일 한도가 수집원 단위이므로). */
    private static final String API_NAME = "saramin";
    private static final String LOC_CD_PARAM = "loc_cd";

    private final RestClient restClient;
    private final ExternalApiMetrics externalApiMetrics;

    private final String baseUrl;
    private final String path;
    private final String accessKey;

    public SaraminJobSearchClient(
            RestClient restClient,
            ExternalApiMetrics externalApiMetrics,
            @Value("${apis.saramin.base-url:https://oapi.saramin.co.kr}") String baseUrl,
            @Value("${apis.saramin.path:/job-search}") String path,
            @Value("${apis.saramin.access-key:}") String accessKey) {
        this.restClient = restClient;
        this.externalApiMetrics = externalApiMetrics;
        this.baseUrl = baseUrl;
        this.path = path;
        this.accessKey = (accessKey == null) ? "" : accessKey.trim();
    }

    /** access-key 설정 여부. 비어 있으면 호출하지 않는다. */
    public boolean hasAccessKey() {
        return !accessKey.isEmpty();
    }

    /** loc_cd 기반 단건 조회 URI(카드 목록·프로필 표본 공용). start 는 0 고정. */
    public URI regionUri(SaraminApiSpecFile.Request request, String locCd, int count) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam(request.accessKeyParam(), accessKey)
                .queryParam(LOC_CD_PARAM, locCd)
                .queryParam(request.startParam(), 0)
                .queryParam(request.countParam(), count)
                .build().encode().toUri();
    }

    /** 페이지 기반 조회 URI(집계용). extraParams(고정 필터)를 함께 싣는다. */
    public URI pagedUri(SaraminApiSpecFile.Request request, int start, int count) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam(request.accessKeyParam(), accessKey)
                .queryParam(request.startParam(), start)
                .queryParam(request.countParam(), count);

        for (Map.Entry<String, String> extra : request.extraParams().entrySet()) {
            if (extra.getValue() != null && !extra.getValue().isBlank()) {
                builder.queryParam(extra.getKey(), extra.getValue());
            }
        }
        return builder.build().encode().toUri();
    }

    /**
     * 이미 인코딩된 URI 로 GET 을 호출해 본문을 돌려준다. 성공/실패를 메트릭에 남기고,
     * 실패 시 RestClient 예외를 그대로 던진다(호출부가 처리).
     */
    public String get(URI uri) {
        try {
            // uri 는 이미 인코딩된 URI 객체다. String 오버로드로 넘기면 URI 템플릿으로 재해석돼
            // access-key 의 %2B 가 %252B 로 재인코딩된다. URI 오버로드를 유지한다.
            ResponseEntity<String> response = restClient.get()
                    .uri(uri)
                    // RestClient 는 RestTemplate 과 달리 Accept 를 자동으로 채우지 않는다.
                    .accept(MediaType.APPLICATION_JSON, MediaType.ALL)
                    .retrieve()
                    .toEntity(String.class);
            externalApiMetrics.success(API_NAME);
            return response.getBody();
        } catch (RuntimeException e) {
            externalApiMetrics.failure(API_NAME);
            throw e;
        }
    }

    /**
     * 로그용 URL. access-key 값을 가린다.
     *
     * <p>키 파라미터명이 설정으로 바뀔 수 있으므로 파라미터명이 아니라 키 값 자체를 지운다.
     * 값이 URL 인코딩돼 들어간 경우까지 잡으려고 인코딩된 형태도 함께 지운다.
     */
    public String maskedUrl(URI uri) {
        String url = uri.toString();
        if (accessKey.isEmpty()) {
            return url;
        }
        String masked = url.replace(accessKey, "****");
        String encoded = UriUtils.encode(accessKey, StandardCharsets.UTF_8);
        return masked.replace(encoded, "****");
    }
}
