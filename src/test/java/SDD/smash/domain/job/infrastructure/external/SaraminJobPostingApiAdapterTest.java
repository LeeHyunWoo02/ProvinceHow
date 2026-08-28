package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.global.metrics.ExternalApiMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 사람인 채용정보 API 어댑터 테스트.
 *
 * <p><b>실제 외부 API 를 부르지 않는다.</b> MockWebServer 가 사람인 응답을 흉내 낸다.
 * 배포되는 스펙은 매핑표가 비어 있어(passthrough=false) 공고가 unresolved 로 집계되는데,
 * 이 테스트는 요청 파라미터/페이징/전체건수/설정 여부에 집중한다. 매핑은 {@code SaraminCodeMapperTest} 가 본다.
 */
class SaraminJobPostingApiAdapterTest {

    private static final String ACCESS_KEY = "TEST-ACCESS-KEY-1234";

    private MockWebServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void stopServer() throws IOException {
        server.shutdown();
    }

    @Test
    @DisplayName("문자열 total 을 읽고, 매핑표가 비어 있으면 지역 미해결로 집계한다")
    void readsTotalAndCountsUnresolvedWithEmptyMapping() {
        // given
        server.enqueue(json("""
                { "jobs": { "count": 1, "start": 0, "total": "250",
                    "job": { "id": "46203390",
                             "position": { "location": { "code": "101000" },
                                           "job-code": { "code": "84" } } } } }
                """));

        // when
        JobPostingPage page = adapter(ACCESS_KEY, 1).fetchPage(1, 100);

        // then
        assertThat(page.totalCount()).isEqualTo(250);
        assertThat(page.postings()).isEmpty();
        assertThat(page.unresolvedRegionCount()).isEqualTo(1);
        assertThat(page.hasNext(100)).isTrue();
    }

    @Test
    @DisplayName("1-based pageNumber 를 0-based start 로 옮기고 access-key/count 를 싣는다")
    void mapsPageNumberToZeroBasedStart() throws InterruptedException {
        // given
        server.enqueue(json("{ \"jobs\": { \"total\": \"0\" } }"));

        // when - 3페이지 → start=2
        adapter(ACCESS_KEY, 1).fetchPage(3, 50);

        // then
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .contains("/job-search")
                .contains("access-key=" + ACCESS_KEY)
                .contains("start=2")
                .contains("count=50");
    }

    @Test
    @DisplayName("count 는 사람인 상한(110)을 넘지 않는다")
    void clampsCountToApiMaximum() throws InterruptedException {
        // given
        server.enqueue(json("{ \"jobs\": { \"total\": \"0\" } }"));

        // when
        adapter(ACCESS_KEY, 1).fetchPage(1, 5000);

        // then
        assertThat(server.takeRequest().getPath()).contains("count=110").contains("start=0");
    }

    @Test
    @DisplayName("maxPageSize 는 스펙의 사람인 상한(110)이다")
    void maxPageSizeIsSaraminLimit() {
        assertThat(adapter(ACCESS_KEY, 1).maxPageSize()).isEqualTo(110);
    }

    @Test
    @DisplayName("access-key 가 있으면 isConfigured 가 참이다")
    void isConfiguredWhenAccessKeyPresent() {
        assertThat(adapter(ACCESS_KEY, 1).isConfigured()).isTrue();
    }

    @Test
    @DisplayName("access-key 가 비어 있으면 API 를 호출하지 않고 설정 오류를 알린다")
    void refusesToCallApiWithoutAccessKey() {
        // when & then
        assertThat(adapter("", 1).isConfigured()).isFalse();
        assertThatThrownBy(() -> adapter("", 1).fetchPage(1, 100))
                .isInstanceOf(SaraminApiException.class)
                .hasMessageContaining("SARAMIN_ACCESS_KEY");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("시작페이지 상한을 넘으면 호출하지 않고 빈 페이지를 돌려준다")
    void stopsAtStartPageLimitWithoutCallingApi() {
        // when
        JobPostingPage page = adapter(ACCESS_KEY, 1).fetchPage(1001, 100);

        // then
        assertThat(page.postings()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("HTTP 오류가 계속되면 설정한 횟수만큼 재시도한 뒤 실패한다")
    void retriesThenFailsOnPersistentHttpError() {
        // given
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        // when & then
        assertThatThrownBy(() -> adapter(ACCESS_KEY, 3).fetchPage(1, 100))
                .isInstanceOf(SaraminApiException.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("로그용 URL 에서 access-key 를 가린다")
    void masksAccessKeyInLoggableUrl() {
        // given
        SaraminJobPostingApiAdapter adapter = adapter(ACCESS_KEY, 1);
        URI uri = URI.create(server.url("/job-search").toString()
                + "?access-key=" + ACCESS_KEY + "&start=0");

        // when
        String masked = adapter.maskedUrl(uri);

        // then
        assertThat(masked).doesNotContain(ACCESS_KEY).contains("****");
    }

    private MockResponse json(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json;charset=UTF-8")
                .setBody(body);
    }

    private SaraminJobPostingApiAdapter adapter(String accessKey, int maxAttempts) {
        SaraminApiSpecLoader specLoader = new SaraminApiSpecLoader(
                new ObjectMapper(), new DefaultResourceLoader(), "classpath:saramin/saramin-job-api.json");
        return new SaraminJobPostingApiAdapter(
                RestClient.create(),
                new SaraminJobPostingParser(new ObjectMapper()),
                new SaraminCodeMapper(specLoader),
                specLoader,
                server.url("/").toString().replaceAll("/$", ""),
                "/job-search",
                accessKey,
                maxAttempts,
                0L,
                new ExternalApiMetrics(new SimpleMeterRegistry()));
    }
}
