package SDD.smash.domain.dwelling.infrastructure.external;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentDataStatus;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 국토부 API 를 호출하지 않는다. {@code MockRestServiceServer} 로 합성 응답만 쓴다.
 */
class MolitAptRentApiAdapterTest {

    private static final SigunguCode GANGNAM = SigunguCode.of("11680");
    private static final YearMonth MAY_2026 = YearMonth.of(2026, 5);

    /** 운영 설정(backend.env.example)의 MOLIT_BASE_URL / MOLIT_PATH 와 같은 모양이다. */
    private static final String BASE_URL = "http://localhost/1613000";
    private static final String API_PATH = "/RTMSDataSvcAptRent/getRTMSDataSvcAptRent";

    private RestTemplate restTemplate;
    private MockRestServiceServer server;
    private MolitAptRentApiAdapter adapter;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).ignoreExpectOrder(true).build();
        adapter = new MolitAptRentApiAdapter(restTemplate, new ObjectMapper(), new XmlMapper());

        // 운영 설정값과 같은 모양이다. apis.molit.path 는 선행 슬래시가 있고 세그먼트가 2개다.
        ReflectionTestUtils.setField(adapter, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(adapter, "apiPath", API_PATH);
        ReflectionTestUtils.setField(adapter, "serviceKey", "super-secret-key");
        ReflectionTestUtils.setField(adapter, "pageSize", 1000);
        ReflectionTestUtils.setField(adapter, "maxPages", 50);
        ReflectionTestUtils.setField(adapter, "requestIntervalMs", 0L);
        ReflectionTestUtils.setField(adapter, "maxConcurrentRequests", 1);
        adapter.initRateLimiter();
    }

    // ------------------------------------------------------------------ URL 조립

    @Test
    @DisplayName("선행 슬래시가 있는 다중 세그먼트 경로를 baseUrl 뒤에 그대로 이어붙인다")
    void buildsUrlFromPathWithLeadingSlashAndMultipleSegments() {
        // given - pathSegment() 를 쓰면 '/' 때문에 IllegalArgumentException 이 나던 경로다
        server.expect(once(), requestTo(startsWith(
                        "http://localhost/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent?")))
                .andRespond(withSuccess(successBody(1, 1, 1), MediaType.APPLICATION_JSON));

        // when
        List<RentRecord> records = adapter.fetch(GANGNAM, MAY_2026);

        // then
        assertThat(records).hasSize(1);
        server.verify();
    }

    @Test
    @DisplayName("경로 뒤에 조회 파라미터가 모두 붙는다")
    void appendsEveryQueryParameterAfterPath() {
        // given
        server.expect(once(), requestTo(allOf(
                        containsString("/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent?"),
                        containsString("LAWD_CD=11680"),
                        containsString("DEAL_YMD=202605"),
                        containsString("pageNo=1"),
                        containsString("numOfRows=1000"),
                        containsString("_type=json"),
                        containsString("serviceKey=super-secret-key"))))
                .andRespond(withSuccess(successBody(1, 1, 1), MediaType.APPLICATION_JSON));

        // when
        adapter.fetch(GANGNAM, MAY_2026);

        // then
        server.verify();
    }

    // ------------------------------------------------------------------ 페이지네이션

    @Test
    @DisplayName("totalCount 가 한 페이지를 넘으면 남은 페이지까지 모두 가져온다")
    void fetchesEveryPageUntilTotalCountIsReached() {
        // given - 강남구 202605 실측값: totalCount=1892 (기존 구현은 1000건만 받고 892건을 버렸다)
        expectPage(1, successBody(1892, 1000, 1));
        expectPage(2, successBody(1892, 892, 2));

        // when
        List<RentRecord> records = adapter.fetch(GANGNAM, MAY_2026);

        // then
        assertThat(records).hasSize(1892);
        server.verify();
    }

    @Test
    @DisplayName("한 페이지로 끝나면 두 번째 페이지를 호출하지 않는다")
    void doesNotRequestSecondPageWhenFirstPageCoversTotalCount() {
        // given
        expectPage(1, successBody(181, 181, 1));

        // when
        MonthlyRentResult result = adapter.fetchMonth(SigunguCode.of("11110"), MAY_2026);

        // then
        assertThat(result.records()).hasSize(181);
        assertThat(result.apiCalls()).isEqualTo(1);
        assertThat(result.isComplete()).isTrue();
        server.verify();
    }

    @Test
    @DisplayName("페이지 상한에 걸리면 더 호출하지 않고 미달 사실을 결과에 남긴다")
    void stopsAtConfiguredPageLimit() {
        // given
        ReflectionTestUtils.setField(adapter, "maxPages", 1);
        adapter.initRateLimiter();
        expectPage(1, successBody(1892, 1000, 1));

        // when
        MonthlyRentResult result = adapter.fetchMonth(GANGNAM, MAY_2026);

        // then
        assertThat(result.records()).hasSize(1000);
        assertThat(result.reportedTotal()).isEqualTo(1892);
        assertThat(result.missingCount()).isEqualTo(892);
        assertThat(result.isComplete()).isFalse();
        server.verify();
    }

    // ------------------------------------------------------------ 빈 응답 vs 실제 0건

    @Test
    @DisplayName("resultCode 가 정상이고 totalCount 가 0이면 실제 0건으로 확정한다")
    void treatsZeroTotalCountAsConfirmedEmpty() {
        // given - 존재하지 않는 LAWD_CD 도 오류가 아니라 이 형태로 온다
        expectPage(1, emptyBody());

        // when
        MonthlyRentResult result = adapter.fetchMonth(SigunguCode.of("41110"), MAY_2026);

        // then
        assertThat(result.status()).isEqualTo(RentDataStatus.CONFIRMED_EMPTY);
        assertThat(result.records()).isEmpty();
        assertThat(result.reportedTotal()).isZero();
    }

    @Test
    @DisplayName("totalCount 를 읽을 수 없으면 0건으로 단정하지 않고 판정 불가로 다룬다")
    void treatsMissingTotalCountAsUndetermined() {
        // given
        expectPage(1, """
                {"response":{"header":{"resultCode":"000","resultMsg":"OK"},
                 "body":{"items":{"item":[]}}}}""");

        // when
        MonthlyRentResult result = adapter.fetchMonth(GANGNAM, MAY_2026);

        // then
        assertThat(result.status()).isEqualTo(RentDataStatus.UNDETERMINED);
        assertThat(result.failureReason()).contains("totalCount");
    }

    @Test
    @DisplayName("게이트웨이가 인증을 거부하면 빈 리스트가 아니라 예외로 알린다")
    void throwsWhenGatewayRejectsRequest() {
        // given
        expectPage(1, """
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                   "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                   "returnAuthMsg":"등록되지 않은 서비스키","returnReasonCode":"30"}}}""");

        // when / then
        assertThatThrownBy(() -> adapter.fetch(GANGNAM, MAY_2026))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("SERVICE_KEY_IS_NOT_REGISTERED_ERROR")
                .hasMessageContaining("30");
    }

    @Test
    @DisplayName("실패 resultCode 는 성공으로 취급하지 않는다")
    void throwsWhenResultCodeIsNotSuccess() {
        // given
        expectPage(1, """
                {"response":{"header":{"resultCode":"22","resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},
                 "body":{"items":"","totalCount":0}}}""");

        // when / then
        assertThatThrownBy(() -> adapter.fetch(GANGNAM, MAY_2026))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("22");
    }

    // ------------------------------------------------------------------ HTTP 오류

    @Test
    @DisplayName("HTTP 오류는 삼키지 않고 그대로 던져 Step 재시도에 맡긴다")
    void propagatesHttpErrorSoThatStepCanRetry() {
        // given
        server.expect(once(), requestTo(containsString("pageNo=1"))).andRespond(withServerError());

        // when / then
        assertThatThrownBy(() -> adapter.fetch(GANGNAM, MAY_2026))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("HTTP 오류라도 관대 조회는 예외 대신 판정 불가 상태를 돌려준다")
    void convertsHttpErrorIntoUndeterminedStatus() {
        // given
        server.expect(once(), requestTo(containsString("pageNo=1"))).andRespond(withServerError());

        // when
        MonthlyRentResult result = adapter.fetchMonth(GANGNAM, MAY_2026);

        // then
        assertThat(result.status()).isEqualTo(RentDataStatus.UNDETERMINED);
        assertThat(result.records()).isEmpty();
    }

    @Test
    @DisplayName("XML 로 응답해도 파싱해 같은 결과를 낸다")
    void parsesXmlResponseAsFallback() {
        // given
        String xml = """
                <response><header><resultCode>000</resultCode><resultMsg>OK</resultMsg></header>
                <body><items><item><aptNm>테스트</aptNm><jibun>1</jibun>
                <deposit>95,000</deposit><monthlyRent>20</monthlyRent></item></items>
                <totalCount>1</totalCount></body></response>""";
        server.expect(once(), requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(xml, MediaType.APPLICATION_XML));

        // when
        List<RentRecord> records = adapter.fetch(GANGNAM, MAY_2026);

        // then
        assertThat(records).hasSize(1);
        assertThat(records.get(0).deposit()).isEqualTo(95000);
        assertThat(records.get(0).monthlyRent()).isEqualTo(20);
    }

    // ------------------------------------------------------------------ 구간 수집

    @Test
    @DisplayName("일부 월이 실패해도 나머지를 계속 모으고 실패한 월을 결과에 남긴다")
    void collectsWholePeriodAndReportsFailedMonths() {
        // given - 2026-04 정상 / 2026-05 게이트웨이 오류 / 2026-06 정상
        expectMonth("202604", successBody(2, 2, 1));
        expectMonth("202605", """
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                   "errMsg":"SERVICETIMEOUT_ERROR","returnReasonCode":"05"}}}""");
        expectMonth("202606", successBody(3, 3, 1));

        // when
        RentCollection collection =
                adapter.collect(GANGNAM, AggregationPeriod.endingAt(YearMonth.of(2026, 6), 3));

        // then
        assertThat(collection.hasFailures()).isTrue();
        assertThat(collection.isComplete()).isFalse();
        assertThat(collection.failedMonths()).containsExactly(YearMonth.of(2026, 5));
        assertThat(collection.recordCount()).isEqualTo(5);
        assertThat(collection.confirmedMonthCount()).isEqualTo(2);
        server.verify();
    }

    @Test
    @DisplayName("모든 월이 정상이면 실패 목록이 비고 호출 수가 집계된다")
    void collectsWholePeriodWithoutFailures() {
        // given
        expectMonth("202605", successBody(1, 1, 1));
        expectMonth("202606", emptyBody());

        // when
        RentCollection collection =
                adapter.collect(GANGNAM, AggregationPeriod.endingAt(YearMonth.of(2026, 6), 2));

        // then
        assertThat(collection.isComplete()).isTrue();
        assertThat(collection.failedMonths()).isEmpty();
        assertThat(collection.confirmedEmptyMonths()).containsExactly(YearMonth.of(2026, 6));
        assertThat(collection.apiCalls()).isEqualTo(2);
        assertThat(collection.recordCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 비밀값

    @Test
    @DisplayName("로그용 URL 에서 serviceKey 가 마스킹된다")
    void masksServiceKeyInUrl() {
        String masked = MolitAptRentApiAdapter.mask(
                "http://x/y?LAWD_CD=11680&serviceKey=abc%2BdEf%3D&_type=json");

        assertThat(masked).doesNotContain("abc%2BdEf%3D");
        assertThat(masked).contains("serviceKey=****");
        assertThat(masked).contains("LAWD_CD=11680");
    }

    // ------------------------------------------------------------------ fixture

    private void expectPage(int pageNo, String body) {
        server.expect(once(), requestTo(containsString("pageNo=" + pageNo)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private void expectMonth(String dealYmd, String body) {
        server.expect(once(), requestTo(containsString("DEAL_YMD=" + dealYmd)))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }

    private static String successBody(int totalCount, int itemCount, int pageNo) {
        StringBuilder items = new StringBuilder();
        for (int i = 0; i < itemCount; i++) {
            if (i > 0) {
                items.append(',');
            }
            items.append("{\"aptNm\":\"테스트아파트\",\"jibun\":\"1\",")
                    .append("\"deposit\":\"95,000\",\"monthlyRent\":\"")
                    .append(i % 2 == 0 ? 0 : 20)
                    .append("\"}");
        }
        return "{\"response\":{\"header\":{\"resultCode\":\"000\",\"resultMsg\":\"OK\"},"
                + "\"body\":{\"items\":{\"item\":[" + items + "]},"
                + "\"numOfRows\":1000,\"pageNo\":" + pageNo + ",\"totalCount\":" + totalCount + "}}}";
    }

    private static String emptyBody() {
        return "{\"response\":{\"header\":{\"resultCode\":\"000\",\"resultMsg\":\"OK\"},"
                + "\"body\":{\"items\":\"\",\"numOfRows\":1,\"pageNo\":1,\"totalCount\":0}}}";
    }
}
