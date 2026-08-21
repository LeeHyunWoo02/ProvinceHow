package SDD.smash.domain.address.infrastructure.external;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.ExternalApiMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * KOSIS 어댑터 테스트. <b>실제 KOSIS 를 호출하지 않는다</b> —
 * {@code MockRestServiceServer} 와 문서의 응답 예시를 본떠 직접 만든 합성 fixture 만 쓴다.
 */
class KosisPopulationApiAdapterTest {

    private static final YearMonth JUNE = YearMonth.of(2026, 6);
    private static final YearMonth JULY = YearMonth.of(2026, 7);
    private static final YearMonth AUGUST = YearMonth.of(2026, 8);

    private RestTemplate restTemplate;
    private MockRestServiceServer server;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
    }

    @Test
    @DisplayName("총인구수 시군구 행만 남기고 전국·시도 합계와 읍면동은 걸러낸다")
    void keepsOnlySigunguTotalPopulationRows() {
        // given
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));

        // when
        List<PopulationSnapshot> snapshots = adapter().fetch(JUNE);

        // then - 전국(00) / 시도(11) / 읍면동(1111051) / 다른 항목(T21) / 통계부호(-) 가 모두 빠진다
        assertThat(snapshots).containsExactly(
                PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, JUNE),
                PopulationSnapshot.of(SigunguCode.of("11140"), 120_000, JUNE),
                PopulationSnapshot.of(SigunguCode.of("99999"), 30_000, JUNE));
        server.verify();
    }

    @Test
    @DisplayName("확인된 파라미터만 보내고 의미 미확인인 jsonVD 는 설정이 비면 붙이지 않는다")
    void sendsConfirmedParametersAndOmitsUnconfirmedJsonVd() {
        // given
        server.expect(requestTo(allOf(
                        containsString("/openapi/Param/statisticsParameterData.do"),
                        containsString("method=getList"),
                        containsString("orgId=101"),
                        containsString("tblId=DT_1B040A3"),
                        containsString("objL1=ALL"),
                        containsString("itmId=T20"),
                        containsString("prdSe=M"),
                        containsString("format=json"),
                        containsString("apiKey=test-kosis-key"),
                        not(containsString("jsonVD")))))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));

        // when
        adapter().fetch(JUNE);

        // then
        server.verify();
    }

    @Test
    @DisplayName("설정하면 jsonVD 를 붙인다")
    void appendsJsonVdWhenConfigured() {
        // given
        server.expect(requestTo(containsString("jsonVD=Y")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));

        // when
        adapter("test-kosis-key", "Y", 3, 0, 3).fetch(JUNE);

        // then
        server.verify();
    }

    @Test
    @DisplayName("빈 배열 응답이면 빈 목록을 돌려준다")
    void returnsEmptyListWhenResponseHasNoRows() {
        // given
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-empty.json"), MediaType.APPLICATION_JSON));

        // when
        List<PopulationSnapshot> snapshots = adapter().fetch(JUNE);

        // then
        assertThat(snapshots).isEmpty();
    }

    @Test
    @DisplayName("HTTP 200 이어도 본문에 err 가 있으면 실패로 판정하고 재시도하지 않는다")
    void failsOnErrorBodyReturnedWithHttpOk() {
        // given
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("error-invalid-key.json"), MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> adapter().fetch(JUNE))
                .isInstanceOf(KosisApiException.class)
                .satisfies(e -> assertThat(((KosisApiException) e).errorCode()).isEqualTo("11"));
        server.verify();   // 호출은 1회뿐이다
    }

    @Test
    @DisplayName("서버 오류는 재시도하고 성공하면 그 결과를 돌려준다")
    void retriesOnServerErrorThenSucceeds() {
        // given
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));

        // when
        List<PopulationSnapshot> snapshots = adapter().fetch(JUNE);

        // then
        assertThat(snapshots).hasSize(3);
        server.verify();
    }

    @Test
    @DisplayName("재시도를 모두 소진하면 KosisApiException 으로 끝낸다")
    void failsAfterExhaustingRetries() {
        // given
        for (int i = 0; i < 3; i++) {
            server.expect(requestTo(containsString("startPrdDe=202606")))
                    .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        }

        // when / then
        assertThatThrownBy(() -> adapter().fetch(JUNE)).isInstanceOf(KosisApiException.class);
        server.verify();
    }

    @Test
    @DisplayName("400 대 오류는 재시도하지 않고 즉시 실패한다")
    void doesNotRetryClientError() {
        // given
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        // when / then
        assertThatThrownBy(() -> adapter().fetch(JUNE)).isInstanceOf(KosisApiException.class);
        server.verify();
    }

    @Test
    @DisplayName("최신 확정 월이 기준월 이하면 한 번의 호출로 끝낸다")
    void usesLatestMonthWithSingleCallWhenNotAfterBaseMonth() {
        // given
        server.expect(requestTo(containsString("newEstPrdCnt=1")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));

        // when
        List<PopulationSnapshot> snapshots = adapter().fetchLatestNotAfter(AUGUST);

        // then
        assertThat(snapshots).hasSize(3);
        assertThat(snapshots).allSatisfy(s -> assertThat(s.statisticsMonth()).isEqualTo(JUNE));
        server.verify();
    }

    @Test
    @DisplayName("최신 확정 월이 기준월보다 뒤면 직전 확정 월로 되짚는다")
    void fallsBackToPreviousMonthWhenLatestIsAfterBaseMonth() {
        // given - 최신은 2026-07 인데 기준월은 2026-06 이다
        server.expect(requestTo(containsString("newEstPrdCnt=1")))
                .andRespond(withSuccess(fixture("population-202607.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));

        // when
        List<PopulationSnapshot> snapshots = adapter().fetchLatestNotAfter(JUNE);

        // then
        assertThat(snapshots).allSatisfy(s -> assertThat(s.statisticsMonth()).isEqualTo(JUNE));
        assertThat(snapshots).hasSize(3);
        server.verify();
    }

    @Test
    @DisplayName("되짚을 개월 수 안에서 자료를 못 찾으면 빈 목록을 돌려준다")
    void returnsEmptyWhenNoConfirmedMonthWithinFallbackWindow() {
        // given - 탐색 1회 + 기준월 포함 2개월(fallbackMonths=1)
        server.expect(requestTo(containsString("newEstPrdCnt=1")))
                .andRespond(withSuccess(fixture("population-empty.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("startPrdDe=202607")))
                .andRespond(withSuccess(fixture("population-empty.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-empty.json"), MediaType.APPLICATION_JSON));

        // when
        List<PopulationSnapshot> snapshots =
                adapter("test-kosis-key", "", 3, 0, 1).fetchLatestNotAfter(JULY);

        // then
        assertThat(snapshots).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("인증키가 없으면 KOSIS 를 호출하지 않는다")
    void neverCallsApiWithoutApiKey() {
        // given - 기대 요청을 하나도 등록하지 않았다. 호출하면 테스트가 깨진다
        KosisPopulationApiAdapter adapter = adapter("", "", 3, 0, 3);

        // when / then
        assertThat(adapter.isAvailable()).isFalse();
        assertThatThrownBy(() -> adapter.fetch(JUNE))
                .isInstanceOf(KosisApiException.class)
                .hasMessageContaining("apis.kosis.base-url");
        assertThatThrownBy(() -> adapter.fetchLatestNotAfter(JUNE))
                .isInstanceOf(KosisApiException.class);
        server.verify();
    }

    @Test
    @DisplayName("같은 기준월로 다시 호출하면 같은 결과가 나온다")
    void returnsSameSnapshotsForSameMonthOnRerun() {
        // given
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));
        server.expect(requestTo(containsString("startPrdDe=202606")))
                .andRespond(withSuccess(fixture("population-202606.json"), MediaType.APPLICATION_JSON));
        KosisPopulationApiAdapter adapter = adapter();

        // when
        List<PopulationSnapshot> first = adapter.fetch(JUNE);
        List<PopulationSnapshot> second = adapter.fetch(JUNE);

        // then
        assertThat(second).isEqualTo(first);
        server.verify();
    }

    @Test
    @DisplayName("로그용 URL 에서 apiKey 값을 가린다")
    void masksApiKeyInUrl() {
        String masked = KosisPopulationApiAdapter.maskApiKey(
                "https://kosis.kr/openapi/Param/statisticsParameterData.do?orgId=101&apiKey=SECRET123&format=json");

        assertThat(masked).contains("apiKey=***");
        assertThat(masked).doesNotContain("SECRET123");
        assertThat(masked).contains("orgId=101");
    }

    // --- helpers ---------------------------------------------------------

    private KosisPopulationApiAdapter adapter() {
        return adapter("test-kosis-key", "", 3, 0, 3);
    }

    private KosisPopulationApiAdapter adapter(String apiKey, String jsonVd,
                                              int maxAttempts, long backoffMillis, int fallbackMonths) {
        return new KosisPopulationApiAdapter(
                restTemplate,
                new ObjectMapper(),
                "https://kosis.kr/openapi",
                apiKey,
                "101",
                "DT_1B040A3",
                "T20",
                "M",
                jsonVd,
                maxAttempts,
                backoffMillis,
                fallbackMonths,
                new ExternalApiMetrics(new SimpleMeterRegistry()));
    }

    private static String fixture(String name) {
        try (InputStream in = KosisPopulationApiAdapterTest.class
                .getResourceAsStream("/fixtures/kosis/" + name)) {
            if (in == null) throw new IllegalStateException("fixture 없음: " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
