package SDD.smash.domain.dwelling.infrastructure.external;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.MonthlyRentResult;
import SDD.smash.domain.dwelling.domain.model.RentCollection;
import SDD.smash.domain.dwelling.domain.model.RentDataStatus;
import SDD.smash.domain.dwelling.domain.model.RentRecord;
import SDD.smash.domain.dwelling.domain.service.RentStatCalculator;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.metrics.CallBudgetMetrics;
import SDD.smash.global.metrics.ExternalApiMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 국토부 API 를 호출하지 않는다. {@code MockRestServiceServer} 로 합성 응답만 쓴다.
 */
class MolitRentApiAdapterTest {

    private static final SigunguCode GANGNAM = SigunguCode.of("11680");
    private static final YearMonth MAY_2026 = YearMonth.of(2026, 5);

    /** 운영 설정(backend.env.example)의 MOLIT_BASE_URL / MOLIT_PATH_* 와 같은 모양이다. */
    private static final String BASE_URL = "http://localhost/1613000";
    private static final String API_PATH = "/RTMSDataSvcAptRent/getRTMSDataSvcAptRent";
    private static final String RH_PATH = "/RTMSDataSvcRHRent/getRTMSDataSvcRHRent";
    private static final String SH_PATH = "/RTMSDataSvcSHRent/getRTMSDataSvcSHRent";

    private RestClient restClient;
    private MockRestServiceServer server;
    private MolitRentApiAdapter adapter;

    @BeforeEach
    void setUp() {
        // RestClient 는 빌더에 바인딩한 뒤 build() 한 인스턴스를 어댑터에 넘겨야 목 서버가 붙는다.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        restClient = builder.build();
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        adapter = new MolitRentApiAdapter(restClient, new ObjectMapper(), new XmlMapper(),
                new ExternalApiMetrics(meterRegistry), new CallBudgetMetrics(meterRegistry));

        // 운영 설정값과 같은 모양이다. apis.molit.paths.* 는 선행 슬래시가 있고 세그먼트가 2개다.
        ReflectionTestUtils.setField(adapter, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(adapter, "apartmentPath", API_PATH);
        ReflectionTestUtils.setField(adapter, "multiplexHousePath", RH_PATH);
        ReflectionTestUtils.setField(adapter, "detachedHousePath", SH_PATH);
        ReflectionTestUtils.setField(adapter, "serviceKey", "super-secret-key");
        ReflectionTestUtils.setField(adapter, "pageSize", 1000);
        ReflectionTestUtils.setField(adapter, "maxPages", 50);
        ReflectionTestUtils.setField(adapter, "requestIntervalMs", 0L);
        ReflectionTestUtils.setField(adapter, "maxConcurrentRequests", 1);
        ReflectionTestUtils.setField(adapter, "dailyCallBudget", 9000);
        // 운영과 같은 총 3회 시도. 대기만 없앤다
        ReflectionTestUtils.setField(adapter, "retryMaxAttempts", 3);
        ReflectionTestUtils.setField(adapter, "retryBackoffMs", 0L);
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
        List<RentRecord> records = adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

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
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then
        server.verify();
    }

    /** 인증키를 뺀 공통 URL. 두 인코딩 케이스가 이 뒤에 serviceKey 만 다르게 붙인다. */
    private static final String URL_WITHOUT_KEY =
            "http://localhost/1613000/RTMSDataSvcAptRent/getRTMSDataSvcAptRent"
                    + "?LAWD_CD=11680&DEAL_YMD=202605&pageNo=1&numOfRows=1000&_type=json";

    @Test
    @DisplayName("이미 인코딩된 인증키를 다시 인코딩하지 않는다")
    void sendsAlreadyEncodedServiceKeyWithoutReEncoding() {
        // given - data.go.kr 의 Encoding 키 모양이다. %2B 가 %252B 가 되면 인증이 깨진다
        ReflectionTestUtils.setField(adapter, "serviceKey", "abc%2Bdef%2Fghi%3D");

        // when
        URI requested = captureRequestUri();

        // then
        assertThat(requested.toString()).isEqualTo(URL_WITHOUT_KEY + "&serviceKey=abc%2Bdef%2Fghi%3D");
    }

    @Test
    @DisplayName("인코딩되지 않은 인증키는 값을 바꾸지 않고 그대로 실어 보낸다")
    void sendsPlainServiceKeyUnchanged() {
        // given - data.go.kr 의 Decoding 키 모양이다
        ReflectionTestUtils.setField(adapter, "serviceKey", "abc+def/ghi");

        // when
        URI requested = captureRequestUri();

        // then
        assertThat(requested.toString()).isEqualTo(URL_WITHOUT_KEY + "&serviceKey=abc+def/ghi");
    }

    /** 실제로 나간 요청 URI 를 한 건 잡아 돌려준다. */
    private URI captureRequestUri() {
        AtomicReference<URI> seen = new AtomicReference<>();
        server.expect(once(), request -> seen.set(request.getURI()))
                .andRespond(withSuccess(successBody(1, 1, 1), MediaType.APPLICATION_JSON));
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);
        server.verify();
        return seen.get();
    }

    @Test
    @DisplayName("주택유형마다 서로 다른 엔드포인트로 요청한다")
    void routesEachHousingTypeToItsOwnEndpoint() {
        // given - 유형을 무시하고 아파트 경로로 부르면 다른 유형의 자료가 조용히 아파트 자료로 섞인다
        expectPath(API_PATH);
        expectPath(RH_PATH);
        expectPath(SH_PATH);

        // when
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);
        adapter.fetch(HousingType.MULTIPLEX_HOUSE, GANGNAM, MAY_2026);
        adapter.fetch(HousingType.DETACHED_HOUSE, GANGNAM, MAY_2026);

        // then
        server.verify();
    }

    @Test
    @DisplayName("유형별 경로 설정이 비어 있으면 부팅 초기화에서 실패한다")
    void failsInitializationWhenPathIsNotConfigured() {
        // given - MOLIT_PATH_DETACHED_HOUSE= 처럼 빈 값으로 정의된 경우다.
        //         런타임에 흡수되면 배치가 COMPLETED 로 끝나면서 그 유형이 전국에서 사라진다
        ReflectionTestUtils.setField(adapter, "detachedHousePath", "  ");

        // when / then
        assertThatThrownBy(() -> adapter.initRateLimiter())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DETACHED_HOUSE");
    }

    @Test
    @DisplayName("초기화 이후 경로가 비어도 아파트로 폴백하지 않고 실패한다")
    void failsInsteadOfFallingBackWhenPathIsNotConfigured() {
        // given - 부팅 검증을 통과한 뒤의 방어 가드다. 조용한 폴백은 다른 유형의 자료를 섞어버린다
        ReflectionTestUtils.setField(adapter, "detachedHousePath", "  ");

        // when / then
        assertThatThrownBy(() -> adapter.fetch(HousingType.DETACHED_HOUSE, GANGNAM, MAY_2026))
                .isInstanceOf(MolitApiException.class)
                .hasMessageContaining("DETACHED_HOUSE");
    }

    @Test
    @DisplayName("base-url 에 서비스명이 남아 있어도 떼어내고 유형별 경로를 붙인다")
    void stripsLegacyServiceNameFromBaseUrl() {
        // given - 운영 backend.env 의 옛 값 모양이다
        ReflectionTestUtils.setField(adapter, "baseUrl", "http://localhost/1613000/RTMSDataSvcAptRent/");
        adapter.initRateLimiter();
        expectPath(RH_PATH);

        // when
        adapter.fetch(HousingType.MULTIPLEX_HOUSE, GANGNAM, MAY_2026);

        // then
        server.verify();
    }

    // ------------------------------------------------------------------ 유형별 필드 매핑

    @Test
    @DisplayName("연립다세대는 건물명을 mhouseNm 에서 읽는다")
    void mapsMultiplexHouseBuildingNameFromMhouseNm() {
        // given - docs/external-api-spec.md 4.4 의 실측 응답이다
        expectPage(1, itemsBody("""
                {"buildYear":2018,"dealDay":20,"dealMonth":6,"dealYear":2026,"deposit":"4,000",
                 "excluUseAr":43.845,"floor":4,"houseType":"다세대","jibun":"1180-1",
                 "mhouseNm":"신양빌라","monthlyRent":94,"sggCd":11680,"umdNm":"개포동"}"""));

        // when
        List<RentRecord> records = adapter.fetch(HousingType.MULTIPLEX_HOUSE, GANGNAM, MAY_2026);

        // then
        RentRecord record = records.get(0);
        assertThat(record.buildingName()).isEqualTo("신양빌라");
        assertThat(record.jibun()).isEqualTo("1180-1");
        assertThat(record.deposit()).isEqualTo(4000);
        assertThat(record.monthlyRent()).isEqualTo(94);
        assertThat(record.isMonthly()).isTrue();
    }

    @Test
    @DisplayName("단독다가구는 건물명·지번이 응답에 없어 null 이 된다")
    void mapsDetachedHouseWithoutBuildingNameAndJibun() {
        // given - docs/external-api-spec.md 4.4 의 실측 응답이다
        expectPage(1, itemsBody("""
                {"buildYear":" ","dealDay":28,"dealMonth":6,"dealYear":2026,"deposit":"18,000",
                 "houseType":"다가구","monthlyRent":40,"sggCd":11680,"totalFloorAr":52,"umdNm":"논현동"}"""));

        // when
        List<RentRecord> records = adapter.fetch(HousingType.DETACHED_HOUSE, GANGNAM, MAY_2026);

        // then
        RentRecord record = records.get(0);
        assertThat(record.buildingName()).isNull();
        assertThat(record.jibun()).isNull();
        assertThat(record.deposit()).isEqualTo(18000);
        assertThat(record.monthlyRent()).isEqualTo(40);
    }

    @Test
    @DisplayName("건물명·지번이 null 이어도 전세/월세 판정과 평균·중앙값 집계가 정상이다")
    void aggregatesDetachedHouseRecordsEvenWithoutBuildingName() {
        // given - 월세 40 / 전세(월세 0) 2건
        expectPage(1, itemsBody("""
                {"deposit":"18,000","monthlyRent":40,"houseType":"다가구","umdNm":"논현동"}""", """
                {"deposit":"30,000","monthlyRent":0,"houseType":"단독","umdNm":"논현동"}""", """
                {"deposit":"20,000","monthlyRent":0,"houseType":"단독","umdNm":"논현동"}"""));

        // when
        List<RentRecord> records = adapter.fetch(HousingType.DETACHED_HOUSE, GANGNAM, MAY_2026);

        // then
        assertThat(records).allSatisfy(record -> assertThat(record.buildingName()).isNull());
        assertThat(records.stream().filter(RentRecord::isMonthly)).hasSize(1);

        List<Integer> jeonseDeposits = records.stream()
                .filter(RentRecord::isJeonse).map(RentRecord::deposit).toList();
        assertThat(RentStatCalculator.mean(jeonseDeposits)).isEqualTo(25000.0);
        assertThat(RentStatCalculator.median(jeonseDeposits)).isEqualTo(25000);
    }

    // ------------------------------------------------------------------ 호출 제한 공유

    @Test
    @DisplayName("유형이 달라도 하나의 세마포어와 최소 호출 간격을 공유한다")
    void sharesRateLimiterAcrossHousingTypes() {
        // given - 유형별로 어댑터를 쪼개면 각자 제한을 갖게 되어 일일 호출 한도 통제가 무너진다
        ReflectionTestUtils.setField(adapter, "requestIntervalMs", 60L);
        expectPath(API_PATH);
        expectPath(RH_PATH);
        expectPath(SH_PATH);

        // when
        long startedAt = System.currentTimeMillis();
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);
        adapter.fetch(HousingType.MULTIPLEX_HOUSE, GANGNAM, MAY_2026);
        adapter.fetch(HousingType.DETACHED_HOUSE, GANGNAM, MAY_2026);
        long elapsed = System.currentTimeMillis() - startedAt;

        // then - 첫 호출은 대기가 없고 이후 두 번이 간격만큼 밀린다
        assertThat(elapsed).isGreaterThanOrEqualTo(100L);
        Semaphore permits = (Semaphore) ReflectionTestUtils.getField(adapter, "concurrencyPermits");
        assertThat(permits.availablePermits()).isEqualTo(1);
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
        List<RentRecord> records = adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

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
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, SigunguCode.of("11110"), MAY_2026);

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
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

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
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, SigunguCode.of("41110"), MAY_2026);

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
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

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
        assertThatThrownBy(() -> adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026))
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
        assertThatThrownBy(() -> adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026))
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
        assertThatThrownBy(() -> adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026))
                .isInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("HTTP 오류라도 관대 조회는 예외 대신 판정 불가 상태를 돌려준다")
    void convertsHttpErrorIntoUndeterminedStatus() {
        // given
        server.expect(once(), requestTo(containsString("pageNo=1"))).andRespond(withServerError());

        // when
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

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
        List<RentRecord> records = adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then
        assertThat(records).hasSize(1);
        assertThat(records.get(0).deposit()).isEqualTo(95000);
        assertThat(records.get(0).monthlyRent()).isEqualTo(20);
    }

    // ------------------------------------------------------------------ 월 단위 재시도

    @Test
    @DisplayName("일시적 읽기 타임아웃은 재시도해 최종적으로 성공한다")
    void retriesTransientTimeoutUntilItSucceeds() {
        // given - 재시도가 없으면 이 (시군구, 유형)의 10개월치가 통째로 버려지고 월 1회 배치라 한 달을 기다린다
        server.expect(once(), requestTo(containsString("pageNo=1")))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));
        server.expect(once(), requestTo(containsString("pageNo=1")))
                .andRespond(withSuccess(successBody(1, 1, 1), MediaType.APPLICATION_JSON));

        // when
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then - 재시도한 호출도 실제로 HTTP 를 쏘므로 일일 예산을 2회 소모한다
        assertThat(result.status()).isEqualTo(RentDataStatus.AVAILABLE);
        assertThat(result.records()).hasSize(1);
        assertThat(adapter.callsUsed()).isEqualTo(2);
        server.verify();
    }

    @Test
    @DisplayName("총 시도 횟수를 다 쓰면 판정 불가로 남긴다")
    void givesUpAsUndeterminedAfterEveryAttemptTimesOut() {
        // given - 총 3회 시도
        for (int attempt = 0; attempt < 3; attempt++) {
            server.expect(once(), requestTo(containsString("pageNo=1")))
                    .andRespond(withException(new SocketTimeoutException("Read timed out")));
        }

        // when
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then
        assertThat(result.status()).isEqualTo(RentDataStatus.UNDETERMINED);
        assertThat(adapter.callsUsed()).isEqualTo(3);
        server.verify();
    }

    @Test
    @DisplayName("예산 소진은 재시도하지 않아 호출 수가 늘지 않는다")
    void doesNotRetryWhenDailyCallBudgetIsExhausted() {
        // given - 예산이 없어서 실패한 것을 다시 시도하는 건 무의미하다
        budget(1);
        expectPage(1, successBody(1, 1, 1));
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // when
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then - 추가 요청이 서버로 나가지 않았고 예산 사용량도 그대로다
        assertThat(result.status()).isEqualTo(RentDataStatus.UNDETERMINED);
        assertThat(result.failureReason()).contains("예산 소진");
        assertThat(adapter.callsUsed()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("게이트웨이 오류는 재시도하지 않고 한 번만 호출한다")
    void doesNotRetryGatewayError() {
        // given - 다시 불러도 같은 응답이 온다. 보수적으로 재시도 대상을 타임아웃으로 한정한다
        expectPage(1, """
                {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                   "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR","returnReasonCode":"30"}}}""");

        // when
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then
        assertThat(result.status()).isEqualTo(RentDataStatus.UNDETERMINED);
        assertThat(adapter.callsUsed()).isEqualTo(1);
        server.verify();
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
                adapter.collect(HousingType.APARTMENT, GANGNAM, AggregationPeriod.endingAt(YearMonth.of(2026, 6), 3));

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
                adapter.collect(HousingType.APARTMENT, GANGNAM, AggregationPeriod.endingAt(YearMonth.of(2026, 6), 2));

        // then
        assertThat(collection.isComplete()).isTrue();
        assertThat(collection.failedMonths()).isEmpty();
        assertThat(collection.confirmedEmptyMonths()).containsExactly(YearMonth.of(2026, 6));
        assertThat(collection.apiCalls()).isEqualTo(2);
        assertThat(collection.recordCount()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 일일 호출 예산

    @Test
    @DisplayName("일일 예산을 다 쓰면 다음 호출은 네트워크에 나가지 않는다")
    void stopsCallingWhenDailyBudgetIsExhausted() {
        // given - 예산 1회. 첫 달은 통과하고 두 번째는 호출 전에 막힌다
        budget(1);
        expectPage(1, successBody(1, 1, 1));

        // when
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then
        assertThat(adapter.callsUsed()).isEqualTo(1);
        assertThat(adapter.hasRemainingCapacity()).isFalse();
        assertThatThrownBy(() -> adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026))
                .isInstanceOf(MolitCallBudgetExceededException.class)
                .hasMessageContaining("일일 호출 예산 소진");
        server.verify();    // 두 번째 요청은 서버로 나가지 않았다
    }

    @Test
    @DisplayName("예산 소진은 확정 0건이 아니라 판정 불가로 이어진다")
    void treatsBudgetExhaustionAsUndeterminedNotConfirmedEmpty() {
        // given - 확정 0건이 되면 '실거래가 없는 지역'이라는 뜻이 되어 평균·중앙값이 조용히 왜곡된다
        budget(1);
        expectPage(1, successBody(1, 1, 1));
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // when
        MonthlyRentResult result = adapter.fetchMonth(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // then
        assertThat(result.status()).isEqualTo(RentDataStatus.UNDETERMINED);
        assertThat(result.status()).isNotEqualTo(RentDataStatus.CONFIRMED_EMPTY);
        assertThat(result.failureReason()).contains("예산 소진");
    }

    @Test
    @DisplayName("예산이 소진된 구간 수집은 모든 달이 실패로 남아 집계에서 제외된다")
    void marksEveryMonthUndeterminedWhenBudgetIsAlreadyExhausted() {
        // given - 예산 1회를 다른 유형이 이미 써버린 상태다
        budget(1);
        expectPage(1, successBody(1, 1, 1));
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);

        // when
        RentCollection collection =
                adapter.collect(HousingType.MULTIPLEX_HOUSE, GANGNAM, AggregationPeriod.endingAt(MAY_2026, 3));

        // then - 확정 0건 목록이 비어 있어야 한다. 여기 들어가면 0건으로 집계된다
        assertThat(collection.failedMonths()).hasSize(3);
        assertThat(collection.confirmedEmptyMonths()).isEmpty();
        assertThat(collection.hasFailures()).isTrue();
        assertThat(collection.apiCalls()).isZero();
    }

    @Test
    @DisplayName("주택유형 3종이 하나의 일일 예산을 공유해 카운트된다")
    void countsEveryHousingTypeAgainstOneSharedBudget() {
        // given - 한도가 3종 데이터셋에 걸쳐 공유되는지 미확인이라 최악(공유)을 가정한다
        budget(3);
        expectPath(API_PATH);
        expectPath(RH_PATH);
        expectPath(SH_PATH);

        // when
        adapter.fetch(HousingType.APARTMENT, GANGNAM, MAY_2026);
        adapter.fetch(HousingType.MULTIPLEX_HOUSE, GANGNAM, MAY_2026);
        adapter.fetch(HousingType.DETACHED_HOUSE, GANGNAM, MAY_2026);

        // then - 유형별로 예산을 따로 갖는다면 여기서 아직 여유가 있어야 한다
        assertThat(adapter.callsUsed()).isEqualTo(3);
        assertThat(adapter.hasRemainingCapacity()).isFalse();
        server.verify();
    }

    @Test
    @DisplayName("호출 예산은 날짜가 바뀌면 리셋돼 다음 날 수집이 이어진다")
    void resetsCallBudgetWhenDayChanges() {
        // given - 예산 1회짜리 어댑터가 오늘 예산을 다 썼다
        budget(1);
        LocalDate day1 = LocalDate.of(2026, 8, 19);

        assertThat(adapter.reserveCall(day1)).isTrue();
        assertThat(adapter.reserveCall(day1)).isFalse();

        // when
        LocalDate day2 = day1.plusDays(1);

        // then - 프로세스가 계속 떠 있어도 다음 날 예산이 살아난다
        assertThat(adapter.reserveCall(day2)).isTrue();
        assertThat(adapter.reserveCall(day2)).isFalse();
    }

    // ------------------------------------------------------------------ 비밀값

    @Test
    @DisplayName("로그용 URL 에서 serviceKey 가 마스킹된다")
    void masksServiceKeyInUrl() {
        String masked = MolitRentApiAdapter.mask(
                "http://x/y?LAWD_CD=11680&serviceKey=abc%2BdEf%3D&_type=json");

        assertThat(masked).doesNotContain("abc%2BdEf%3D");
        assertThat(masked).contains("serviceKey=****");
        assertThat(masked).contains("LAWD_CD=11680");
    }

    // ------------------------------------------------------------------ fixture

    /** 남은 일일 예산을 바꾼다. */
    private void budget(int dailyCallBudget) {
        ReflectionTestUtils.setField(adapter, "dailyCallBudget", dailyCallBudget);
    }

    /** 해당 엔드포인트로 정확히 한 번 요청이 오는지 본다. */
    private void expectPath(String path) {
        server.expect(once(), requestTo(startsWith(BASE_URL + path + "?")))
                .andRespond(withSuccess(successBody(1, 1, 1), MediaType.APPLICATION_JSON));
    }

    /** 실측 응답 조각을 그대로 items 에 담은 성공 응답. */
    private static String itemsBody(String... items) {
        return "{\"response\":{\"header\":{\"resultCode\":\"000\",\"resultMsg\":\"OK\"},"
                + "\"body\":{\"items\":{\"item\":[" + String.join(",", items) + "]},"
                + "\"numOfRows\":1000,\"pageNo\":1,\"totalCount\":" + items.length + "}}}";
    }

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
