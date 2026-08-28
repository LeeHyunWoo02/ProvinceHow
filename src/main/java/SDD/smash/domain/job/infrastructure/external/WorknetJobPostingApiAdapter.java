package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.domain.port.JobPostingProvider;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetApiSpecFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * 공공데이터포털 / 한국고용정보원 <b>워크넷 채용정보 채용목록</b> API 어댑터.
 * {@link JobPostingProvider} 포트 구현이다.
 *
 * <p>호출 방식·인증키 주입·예외 처리는 {@code MolitRentApiAdapter}(국토부 실거래 API) 를 따랐다.
 * 다른 점은 두 가지다.
 * <ul>
 *   <li>워크넷은 <b>XML 만</b> 돌려준다({@code returnType=JSON} → {@code messageCd=004}). 실측 확인</li>
 *   <li>인증키 파라미터명이 {@code serviceKey} 가 아니라 <b>{@code authKey}</b> 다</li>
 * </ul>
 *
 * <h2>보안</h2>
 * <ul>
 *   <li>인증키가 비어 있으면 <b>호출하지 않는다</b>. {@link #isConfigured()} 가 거짓이 되고 배치가 막힌다</li>
 *   <li>로그에 남기는 URL 은 항상 {@link #maskedUrl(URI)} 를 거쳐 인증키를 가린다</li>
 *   <li>응답 본문 전체를 로그로 찍지 않는다</li>
 * </ul>
 *
 * <p>외부 어휘({@code authKey}, {@code callTp}, {@code startPage}, {@code wantedAuthNo})는
 * 이 패키지 밖으로 나가지 않는다.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "apis.job.provider", havingValue = "worknet")
public class WorknetJobPostingApiAdapter implements JobPostingProvider {

    /** 워크넷 채용목록 조회 경로. 공식 엔드포인트다(2026-08-13 실측 200 응답 확인). */
    private static final String DEFAULT_PATH = "/opi/opi/opia/wantedApi.do";

    private final RestClient restClient;
    private final WorknetJobPostingParser parser;
    private final WorknetCodeMapper codeMapper;
    private final WorknetApiSpecLoader specLoader;

    private final String baseUrl;
    private final String path;
    private final String authKey;
    private final int maxAttempts;
    private final long retryDelayMillis;

    public WorknetJobPostingApiAdapter(
            RestClient restClient,
            WorknetJobPostingParser parser,
            WorknetCodeMapper codeMapper,
            WorknetApiSpecLoader specLoader,
            @Value("${worknet.api.base-url:https://openapi.work.go.kr}") String baseUrl,
            @Value("${worknet.api.path:" + DEFAULT_PATH + "}") String path,
            @Value("${apis.datagokr.service-key:}") String authKey,
            @Value("${worknet.api.max-attempts:3}") int maxAttempts,
            @Value("${worknet.api.retry-delay-ms:1000}") long retryDelayMillis) {
        this.restClient = restClient;
        this.parser = parser;
        this.codeMapper = codeMapper;
        this.specLoader = specLoader;
        this.baseUrl = baseUrl;
        this.path = path;
        this.authKey = (authKey == null) ? "" : authKey.trim();
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = Math.max(0, retryDelayMillis);
    }

    @Override
    public boolean isConfigured() {
        return !authKey.isEmpty();
    }

    @Override
    public int maxPageSize() {
        return specLoader.spec().request().maxDisplay();
    }

    @Override
    public JobPostingPage fetchPage(int pageNumber, int pageSize) {
        if (!isConfigured()) {
            throw new WorknetApiException(
                    "[worknet] 인증키가 비어 있다. DATA_GO_KR_SERVICE_KEY(apis.datagokr.service-key) 를 설정하라. "
                            + "빈 키로 API 를 호출하지 않는다.");
        }
        WorknetApiSpecFile spec = specLoader.spec();
        if (pageNumber > spec.request().maxStartPage()) {
            log.warn("[worknet] 시작페이지 상한 초과 page={}, max={} - 수집을 여기서 끊는다.",
                    pageNumber, spec.request().maxStartPage());
            return JobPostingPage.empty(pageNumber);
        }

        URI uri = buildUri(spec.request(), pageNumber, pageSize);
        String body = getWithRetry(uri, pageNumber);

        WorknetJobPostingParser.ParsedPage parsed = parser.parse(body, spec.response());
        WorknetCodeMapper.PageMapping mapped = codeMapper.map(parsed.items());

        log.debug("[worknet] page={} 읽음 raw={}건, 집계대상={}건, 지역미매핑={}건, 직종미매핑={}건",
                pageNumber, parsed.items().size(), mapped.postings().size(),
                mapped.unresolvedRegionCount(), mapped.unresolvedJobCount());

        return new JobPostingPage(pageNumber, parsed.total(), mapped.postings(),
                mapped.unresolvedRegionCount(), mapped.unresolvedJobCount());
    }

    private URI buildUri(WorknetApiSpecFile.Request request, int pageNumber, int pageSize) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam(request.authKeyParam(), authKey)
                .queryParam(request.callTypeParam(), request.callTypeList())
                .queryParam(request.returnTypeParam(), request.returnTypeValue())
                .queryParam(request.startPageParam(), pageNumber)
                .queryParam(request.displayParam(), Math.min(pageSize, request.maxDisplay()));

        for (Map.Entry<String, String> extra : request.extraParams().entrySet()) {
            if (extra.getValue() != null && !extra.getValue().isBlank()) {
                builder.queryParam(extra.getKey(), extra.getValue());
            }
        }
        return builder.build().encode().toUri();
    }

    private String getWithRetry(URI uri, int pageNumber) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                // uri 는 이미 인코딩된 URI 객체다. String 오버로드로 넘기면 URI 템플릿으로 재해석돼
                // authKey 의 %2B 가 %252B 로 재인코딩된다. URI 오버로드를 유지한다.
                ResponseEntity<String> response = restClient.get()
                        .uri(uri)
                        // RestClient 는 RestTemplate 과 달리 Accept 를 자동으로 채우지 않는다. 워크넷은 XML 만 준다.
                        .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.ALL)
                        .retrieve()
                        .toEntity(String.class);
                return response.getBody();
            } catch (RuntimeException e) {
                last = e;
                log.warn("[worknet] 호출 실패 page={}, attempt={}/{}, url={}, reason={}",
                        pageNumber, attempt, maxAttempts, maskedUrl(uri), e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw new WorknetApiException(
                "[worknet] 호출 실패 page=" + pageNumber + ", url=" + maskedUrl(uri), last);
    }

    private void sleepBeforeRetry(int attempt) {
        if (retryDelayMillis <= 0 || attempt >= maxAttempts) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new WorknetApiException("[worknet] 재시도 대기 중 인터럽트", ie);
        }
    }

    /**
     * 로그용 URL. 인증키 값을 가린다.
     *
     * <p>키 파라미터명이 설정으로 바뀔 수 있으므로 파라미터명이 아니라 <b>키 값 자체</b>를 지운다.
     * 값이 URL 인코딩돼 들어간 경우까지 잡으려고 인코딩된 형태도 함께 지운다.
     */
    String maskedUrl(URI uri) {
        String url = uri.toString();
        if (authKey.isEmpty()) {
            return url;
        }
        String masked = url.replace(authKey, "****");
        String encoded = UriUtils.encode(authKey, StandardCharsets.UTF_8);
        return masked.replace(encoded, "****");
    }
}
