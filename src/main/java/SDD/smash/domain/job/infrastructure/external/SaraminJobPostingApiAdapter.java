package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.domain.port.JobPostingProvider;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;

/**
 * 사람인(Saramin) 채용정보 오픈 API 어댑터. {@link JobPostingProvider} 포트 구현이다.
 *
 * <p>워크넷 {@code WorknetJobPostingApiAdapter} 를 대체하는 활성 구현이다.
 * {@code apis.job.provider=saramin}(기본값)일 때만 빈으로 등록되어 포트 충돌을 막는다.
 *
 * <h2>사람인 스펙</h2>
 * <ul>
 *   <li>엔드포인트: {@code GET https://oapi.saramin.co.kr/job-search} (응답 JSON)</li>
 *   <li>인증: {@code access-key} 쿼리 파라미터</li>
 *   <li>페이징: {@code start}(0-based) + {@code count}(<=110). 포트의 1-based pageNumber 를 start 로 옮긴다</li>
 * </ul>
 *
 * <h2>⚠️ 호출 제한</h2>
 * 사람인 오픈 API 는 <b>1일 최대 500회</b> 호출 제한이 있다. maxPageSize(110)로 당겨도
 * 하루 500회면 최대 55,000건까지만 수집할 수 있다. 배치는 이 한도를 넘지 않게 운영해야 한다.
 *
 * <h2>보안</h2>
 * <ul>
 *   <li>access-key 가 비어 있으면 <b>호출하지 않는다</b>. {@link #isConfigured()} 가 거짓이 된다</li>
 *   <li>로그에 남기는 URL 은 항상 {@link SaraminJobSearchClient#maskedUrl(URI)} 를 거쳐 access-key 를 가린다</li>
 *   <li>응답 본문 전체를 로그로 찍지 않는다</li>
 * </ul>
 *
 * <p>외부 어휘({@code access-key}, {@code start}, {@code count}, {@code loc_cd})는 이 패키지 밖으로 나가지 않는다.
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "apis.job.provider", havingValue = "saramin", matchIfMissing = true)
public class SaraminJobPostingApiAdapter implements JobPostingProvider {

    private final SaraminJobSearchClient jobSearchClient;
    private final SaraminJobPostingParser parser;
    private final SaraminCodeMapper codeMapper;
    private final SaraminApiSpecLoader specLoader;

    private final int maxAttempts;
    private final long retryDelayMillis;

    public SaraminJobPostingApiAdapter(
            SaraminJobSearchClient jobSearchClient,
            SaraminJobPostingParser parser,
            SaraminCodeMapper codeMapper,
            SaraminApiSpecLoader specLoader,
            @Value("${apis.saramin.max-attempts:3}") int maxAttempts,
            @Value("${apis.saramin.retry-delay-ms:1000}") long retryDelayMillis) {
        this.jobSearchClient = jobSearchClient;
        this.parser = parser;
        this.codeMapper = codeMapper;
        this.specLoader = specLoader;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMillis = Math.max(0, retryDelayMillis);
    }

    @Override
    public boolean isConfigured() {
        return jobSearchClient.hasAccessKey();
    }

    @Override
    public int maxPageSize() {
        return specLoader.spec().request().maxCount();
    }

    @Override
    public JobPostingPage fetchPage(int pageNumber, int pageSize) {
        if (!isConfigured()) {
            throw new SaraminApiException(
                    "[saramin] access-key 가 비어 있다. SARAMIN_ACCESS_KEY(apis.saramin.access-key) 를 설정하라. "
                            + "빈 키로 API 를 호출하지 않는다.");
        }
        SaraminApiSpecFile spec = specLoader.spec();
        if (pageNumber > spec.request().maxStartPage()) {
            log.warn("[saramin] 시작페이지 상한 초과 page={}, max={} - 수집을 여기서 끊는다.",
                    pageNumber, spec.request().maxStartPage());
            return JobPostingPage.empty(pageNumber);
        }

        int count = Math.min(pageSize, spec.request().maxCount());
        int start = Math.max(0, pageNumber - 1);   // 포트는 1-based, 사람인 start 는 0-based
        URI uri = jobSearchClient.pagedUri(spec.request(), start, count);
        String body = getWithRetry(uri, pageNumber);

        SaraminJobPostingParser.ParsedPage parsed = parser.parse(body, spec.response());
        SaraminCodeMapper.PageMapping mapped = codeMapper.map(parsed.items());

        log.debug("[saramin] page={}(start={}) 읽음 raw={}건, 집계대상={}건, 지역미매핑={}건, 직종미매핑={}건",
                pageNumber, start, parsed.items().size(), mapped.postings().size(),
                mapped.unresolvedRegionCount(), mapped.unresolvedJobCount());

        return new JobPostingPage(pageNumber, parsed.total(), mapped.postings(),
                mapped.unresolvedRegionCount(), mapped.unresolvedJobCount());
    }

    private String getWithRetry(URI uri, int pageNumber) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return jobSearchClient.get(uri);
            } catch (RuntimeException e) {
                last = e;
                log.warn("[saramin] 호출 실패 page={}, attempt={}/{}, url={}, reason={}",
                        pageNumber, attempt, maxAttempts, jobSearchClient.maskedUrl(uri), e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw new SaraminApiException(
                "[saramin] 호출 실패 page=" + pageNumber + ", url=" + jobSearchClient.maskedUrl(uri), last);
    }

    private void sleepBeforeRetry(int attempt) {
        if (retryDelayMillis <= 0 || attempt >= maxAttempts) {
            return;
        }
        try {
            Thread.sleep(retryDelayMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new SaraminApiException("[saramin] 재시도 대기 중 인터럽트", ie);
        }
    }
}
