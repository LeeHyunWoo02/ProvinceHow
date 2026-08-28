package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
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
 * 워크넷 채용정보 API 어댑터 테스트.
 *
 * <p><b>실제 외부 API 를 부르지 않는다.</b> MockWebServer 가 워크넷 응답을 흉내 낸다.
 */
class WorknetJobPostingApiAdapterTest {

    private static final String AUTH_KEY = "TEST-AUTH-KEY-1234";

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
    @DisplayName("정상 응답이면 공고를 우리 코드 체계로 옮겨 돌려준다")
    void returnsMappedPostingsOnNormalResponse() {
        // given
        server.enqueue(xml("""
                <wantedRoot>
                    <total>1</total>
                    <wanted><wantedAuthNo>KJAU1</wantedAuthNo><regionCd>11110</regionCd><jobsCd>011</jobsCd></wanted>
                </wantedRoot>
                """));

        // when
        JobPostingPage page = adapter(AUTH_KEY, 1).fetchPage(1, 100);

        // then
        assertThat(page.totalCount()).isEqualTo(1);
        assertThat(page.postings()).hasSize(1);
        assertThat(page.postings().get(0).regions()).containsExactly(SigunguCode.of("11110"));
    }

    @Test
    @DisplayName("요청에 인증키·호출유형·반환형식·페이지 파라미터를 모두 싣는다")
    void sendsAllRequiredQueryParameters() throws InterruptedException {
        // given
        server.enqueue(xml("<wantedRoot><total>0</total></wantedRoot>"));

        // when
        adapter(AUTH_KEY, 1).fetchPage(3, 50);

        // then
        RecordedRequest request = server.takeRequest();
        assertThat(request.getPath())
                .contains("/opi/opi/opia/wantedApi.do")
                .contains("authKey=" + AUTH_KEY)
                .contains("callTp=L")
                .contains("returnType=XML")
                .contains("startPage=3")
                .contains("display=50");
    }

    @Test
    @DisplayName("출력건수는 API 상한(100건)을 넘지 않는다")
    void clampsDisplayToApiMaximum() throws InterruptedException {
        // given
        server.enqueue(xml("<wantedRoot><total>0</total></wantedRoot>"));

        // when
        adapter(AUTH_KEY, 1).fetchPage(1, 5000);

        // then
        assertThat(server.takeRequest().getPath()).contains("display=100");
    }

    @Test
    @DisplayName("빈 응답이면 빈 페이지를 돌려준다")
    void returnsEmptyPageOnEmptyResponse() {
        // given
        server.enqueue(xml("<wantedRoot><total>0</total></wantedRoot>"));

        // when
        JobPostingPage page = adapter(AUTH_KEY, 1).fetchPage(1, 100);

        // then
        assertThat(page.postings()).isEmpty();
        assertThat(page.isEmpty()).isTrue();
        assertThat(page.hasNext(100)).isFalse();
    }

    @Test
    @DisplayName("전체 건수가 남아 있으면 다음 페이지가 있다고 알린다")
    void reportsNextPageWhileTotalRemains() {
        // given
        server.enqueue(xml("""
                <wantedRoot>
                    <total>250</total>
                    <wanted><wantedAuthNo>KJAU1</wantedAuthNo><regionCd>11110</regionCd><jobsCd>011</jobsCd></wanted>
                </wantedRoot>
                """));

        // when
        JobPostingPage page = adapter(AUTH_KEY, 1).fetchPage(1, 100);

        // then
        assertThat(page.hasNext(100)).isTrue();
    }

    @Test
    @DisplayName("API 가 messageCd 오류를 주면 예외를 던진다")
    void throwsOnApiErrorCodeResponse() {
        // given - 인증키가 틀렸을 때 실제로 오는 응답이다
        server.enqueue(xml("<wantedRoot><message>유효하지 않은 인증키 입니다.</message><messageCd>002</messageCd></wantedRoot>"));

        // when & then
        assertThatThrownBy(() -> adapter(AUTH_KEY, 1).fetchPage(1, 100))
                .isInstanceOf(WorknetApiException.class)
                .hasMessageContaining("002");
    }

    @Test
    @DisplayName("HTTP 오류가 계속되면 설정한 횟수만큼 재시도한 뒤 실패한다")
    void retriesThenFailsOnPersistentHttpError() {
        // given
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));
        server.enqueue(new MockResponse().setResponseCode(500));

        // when & then
        assertThatThrownBy(() -> adapter(AUTH_KEY, 3).fetchPage(1, 100))
                .isInstanceOf(WorknetApiException.class);
        assertThat(server.getRequestCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("첫 호출이 실패해도 재시도해서 성공하면 결과를 돌려준다")
    void recoversOnRetryAfterTransientFailure() {
        // given
        server.enqueue(new MockResponse().setResponseCode(503));
        server.enqueue(xml("""
                <wantedRoot>
                    <total>1</total>
                    <wanted><wantedAuthNo>KJAU1</wantedAuthNo><regionCd>11110</regionCd><jobsCd>011</jobsCd></wanted>
                </wantedRoot>
                """));

        // when
        JobPostingPage page = adapter(AUTH_KEY, 3).fetchPage(1, 100);

        // then
        assertThat(page.postings()).hasSize(1);
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("인증키가 비어 있으면 API 를 호출하지 않고 설정 오류를 알린다")
    void refusesToCallApiWithoutAuthKey() {
        // when & then
        assertThatThrownBy(() -> adapter("", 1).fetchPage(1, 100))
                .isInstanceOf(WorknetApiException.class)
                .hasMessageContaining("DATA_GO_KR_SERVICE_KEY");
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("시작페이지 상한을 넘으면 호출하지 않고 빈 페이지를 돌려준다")
    void stopsAtStartPageLimitWithoutCallingApi() {
        // when
        JobPostingPage page = adapter(AUTH_KEY, 1).fetchPage(1001, 100);

        // then
        assertThat(page.postings()).isEmpty();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("로그용 URL 에서 인증키를 가린다")
    void masksAuthKeyInLoggableUrl() {
        // given
        WorknetJobPostingApiAdapter adapter = adapter(AUTH_KEY, 1);
        URI uri = URI.create(server.url("/opi/opi/opia/wantedApi.do").toString()
                + "?authKey=" + AUTH_KEY + "&callTp=L");

        // when
        String masked = adapter.maskedUrl(uri);

        // then
        assertThat(masked).doesNotContain(AUTH_KEY).contains("****");
    }

    private MockResponse xml(String body) {
        return new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/xml;charset=UTF-8")
                .setBody(body);
    }

    private WorknetJobPostingApiAdapter adapter(String authKey, int maxAttempts) {
        WorknetApiSpecLoader specLoader = new WorknetApiSpecLoader(
                new ObjectMapper(), new DefaultResourceLoader(), "classpath:worknet/worknet-job-api.json");
        return new WorknetJobPostingApiAdapter(
                RestClient.create(),
                new WorknetJobPostingParser(new XmlMapper()),
                new WorknetCodeMapper(specLoader),
                specLoader,
                server.url("/").toString().replaceAll("/$", ""),
                "/opi/opi/opia/wantedApi.do",
                authKey,
                maxAttempts,
                0L);
    }
}
