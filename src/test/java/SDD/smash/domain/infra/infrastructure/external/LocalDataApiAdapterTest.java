package SDD.smash.domain.infra.infrastructure.external;

import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.infrastructure.master.IndustryMaster;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.global.metrics.CallBudgetMetrics;
import SDD.smash.global.metrics.ExternalApiMetrics;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * 실제 외부 API 를 호출하지 않는다. {@code MockRestServiceServer} 로 응답을 합성한다
 * (새 테스트 의존성 없이 {@code spring-boot-starter-test} 안에서 해결된다).
 */
class LocalDataApiAdapterTest {

    /** 계측은 대역이 아니라 실물을 쓴다. 어댑터를 여러 번 만들어도 레지스트리는 공유한다. */
    private static final MeterRegistry METER_REGISTRY = new SimpleMeterRegistry();

    private static final String BASE_URL = "http://localhost";
    private static final IndustryCode RESTAURANT = IndustryCode.of("RESTAURANT");
    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");

    private RestClient restClient;
    private MockRestServiceServer server;
    private InfraMasterCatalog masterCatalog;

    @BeforeEach
    void setUp() {
        // RestClient 는 빌더에 바인딩한 뒤 build() 한 인스턴스를 어댑터에 넘겨야 목 서버가 붙는다.
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(false).build();
        restClient = builder.build();

        masterCatalog = mock(InfraMasterCatalog.class);
        IndustryMasterEntry entry = new IndustryMasterEntry(RESTAURANT, "일반음식점", Major.FOOD,
                "general_restaurants", "15154916", true, true, null);
        given(masterCatalog.industryMaster())
                .willReturn(new IndustryMaster(List.of(entry), Map.of()));
    }

    private LocalDataApiAdapter adapter(String serviceKey, int pageSize) {
        return new LocalDataApiAdapter(restClient, new ObjectMapper(), masterCatalog,
                BASE_URL, serviceKey, pageSize, 10, 0, 9000, 1, 0, 2, 0,
                new ExternalApiMetrics(METER_REGISTRY), new CallBudgetMetrics(METER_REGISTRY));
    }

    private static String body(int totalCount, String... items) {
        return """
                {"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE"},
                 "body":{"totalCount":%d,"items":{"item":[%s]}}}}
                """.formatted(totalCount, String.join(",", items));
    }

    private static String item(String managementNo, String statusCode, String orgCode) {
        return """
                {"MNG_NO":"%s","SALS_STTS_CD":"%s","OPN_ATMY_GRP_CD":"%s","BPLC_NM":"가게"}
                """.formatted(managementNo, statusCode, orgCode);
    }

    private String url(int pageNo, int numOfRows) {
        return BASE_URL + "/1741000/general_restaurants/info"
                + "?serviceKey=test-key&pageNo=" + pageNo + "&numOfRows=" + numOfRows
                + "&returnType=json"
                + "&cond%5BOPN_ATMY_GRP_CD%3A%3AEQ%5D=3000000"
                + "&cond%5BSALS_STTS_CD%3A%3AEQ%5D=01";
    }

    // ------------------------------------------------------------------ 정상

    @Test
    @DisplayName("정상 응답을 사업장으로 옮기고 파라미터를 신 스펙 형식으로 인코딩한다")
    void collectsFacilitiesFromNormalResponse() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(2,
                        item("3000000-101-2016-00350", "01", "3000000"),
                        item("3000000-101-2016-00351", "01", "3000000")), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        assertThat(collection.readCount()).isEqualTo(2);
        assertThat(collection.operatingCount()).isEqualTo(2);
        assertThat(collection.apiCalls()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("Accept 로 JSON 과 */* 를 함께 보낸다")
    void sendsJsonAndWildcardAcceptHeader() {
        // given - RestClient 는 Accept 를 자동으로 채우지 않는다. 지우면 응답 협상이 바뀐다
        server.expect(requestTo(url(1, 100)))
                .andExpect(header(HttpHeaders.ACCEPT, "application/json, */*"))
                .andRespond(withSuccess(body(0), MediaType.APPLICATION_JSON));

        // when
        adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        // then
        server.verify();
    }

    @Test
    @DisplayName("지번주소와 도로명주소를 둘 다 옮긴다 - 일반구 재분배의 단서다")
    void carriesBothAddressFields() {
        String withAddresses = """
                {"MNG_NO":"S-1","SALS_STTS_CD":"01","OPN_ATMY_GRP_CD":"3000000",
                 "LOTNO_ADDR":"경기도 수원시 장안구 정자동 1",
                 "ROAD_NM_ADDR":"경기도 수원시 장안구 정자로 2"}
                """;
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(1, withAddresses), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        assertThat(collection.facilities()).hasSize(1);
        assertThat(collection.facilities().get(0).addressCandidates())
                .containsExactly("경기도 수원시 장안구 정자동 1", "경기도 수원시 장안구 정자로 2");
        server.verify();
    }

    @Test
    @DisplayName("주소 필드가 없는 업종도 그대로 수집된다")
    void collectsFacilitiesWithoutAddressFields() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(1, item("S-2", "01", "3000000")), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        assertThat(collection.facilities().get(0).addressCandidates()).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("totalCount 가 0이면 추가 호출 없이 빈 결과다")
    void returnsEmptyWhenTotalCountIsZero() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(0), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        assertThat(collection.readCount()).isZero();
        assertThat(collection.apiCalls()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("totalCount 만큼 페이지를 끝까지 받는다")
    void followsPaginationUntilTotalCount() {
        server.expect(requestTo(url(1, 2)))
                .andRespond(withSuccess(body(3,
                        item("A-1", "01", "3000000"),
                        item("A-2", "01", "3000000")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2, 2)))
                .andRespond(withSuccess(body(3, item("A-3", "01", "3000000")), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 2).collect(RESTAURANT, JONGNO);

        assertThat(collection.readCount()).isEqualTo(3);
        assertThat(collection.apiCalls()).isEqualTo(2);
        server.verify();
    }

    @Test
    @DisplayName("페이지가 겹쳐 같은 관리번호가 두 번 와도 중복이 제거된다")
    void dropsDuplicatedFacilitiesAcrossPages() {
        server.expect(requestTo(url(1, 2)))
                .andRespond(withSuccess(body(4,
                        item("A-1", "01", "3000000"),
                        item("A-2", "01", "3000000")), MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(2, 2)))
                .andRespond(withSuccess(body(4,
                        item("A-2", "01", "3000000"),
                        item("A-3", "01", "3000000")), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 2).collect(RESTAURANT, JONGNO);

        assertThat(collection.readCount()).isEqualTo(3);
        assertThat(collection.duplicatesDropped()).isEqualTo(1);
    }

    @Test
    @DisplayName("서버가 걸러 주지 못한 폐업·휴업 건은 개수에서 빠진다")
    void filtersOutNonOperatingFacilities() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(3,
                        item("A-1", "01", "3000000"),
                        item("A-2", "03", "3000000"),
                        item("A-3", "02", "3000000")), MediaType.APPLICATION_JSON));

        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        assertThat(collection.readCount()).isEqualTo(3);
        assertThat(collection.operatingCount()).isEqualTo(1);
        assertThat(collection.filteredOutCount()).isEqualTo(2);
    }

    // ------------------------------------------------------------------ 실패

    @Test
    @DisplayName("인증키가 비어 있으면 호출하지 않고 사유를 남긴 채 실패한다")
    void doesNotCallWithoutServiceKey() {
        LocalDataApiAdapter adapter = adapter("", 100);

        assertThat(adapter.isReady()).isFalse();
        assertThatThrownBy(() -> adapter.collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("인증키");

        server.verify(); // 기대한 요청이 없으므로 아무 호출도 없어야 통과한다
    }

    @Test
    @DisplayName("실 응답의 resultCode=0 은 성공으로 처리한다")
    void treatsSingleZeroResultCodeAsSuccess() {
        // given - 운영에서 실제로 오는 형태다. resultMsg 는 "정상" 이다
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"0","resultMsg":"정상"},
                         "body":{"totalCount":1,"items":{"item":[%s]}}}}
                        """.formatted(item("3000000-101-2016-00350", "01", "3000000")),
                        MediaType.APPLICATION_JSON));

        // when
        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        // then
        assertThat(collection.readCount()).isEqualTo(1);
        assertThat(collection.operatingCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("resultCode 가 000 이어도 성공으로 처리한다")
    void treatsTripleZeroResultCodeAsSuccess() {
        // given
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"000","resultMsg":"OK"},
                         "body":{"totalCount":1,"items":{"item":[%s]}}}}
                        """.formatted(item("3000000-101-2016-00350", "01", "3000000")),
                        MediaType.APPLICATION_JSON));

        // when
        FacilityCollection collection = adapter("test-key", 100).collect(RESTAURANT, JONGNO);

        // then
        assertThat(collection.readCount()).isEqualTo(1);
        server.verify();
    }

    @Test
    @DisplayName("0/00/000 이 아닌 resultCode 는 여전히 실패로 알린다")
    void stillThrowsOnNonSuccessResultCode() {
        // given - 트래픽 초과(22)
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"22",
                          "resultMsg":"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"},
                         "body":{"totalCount":0,"items":{"item":[]}}}}
                        """, MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> adapter("test-key", 100).collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("실패 응답")
                .hasMessageContaining("resultCode=22");
    }

    @Test
    @DisplayName("0 으로 시작해도 성공 코드가 아니면 실패로 알린다")
    void stillThrowsOnResultCodeStartingWithZero() {
        // given - "0" 접두 비교로 눙치지 않는다는 확인이다
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess("""
                        {"response":{"header":{"resultCode":"04","resultMsg":"HTTP_ERROR"},
                         "body":{"totalCount":0,"items":{"item":[]}}}}
                        """, MediaType.APPLICATION_JSON));

        // when / then
        assertThatThrownBy(() -> adapter("test-key", 100).collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("resultCode=04");
    }

    @Test
    @DisplayName("게이트웨이 오류 응답은 예외로 알린다 - 빈 결과로 삼키지 않는다")
    void throwsOnGatewayError() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess("""
                        {"OpenAPI_ServiceResponse":{"cmmMsgHeader":{
                          "errMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR",
                          "returnAuthMsg":"등록되지 않은 서비스키","returnReasonCode":"30"}}}
                        """, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> adapter("test-key", 100).collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("게이트웨이 오류");
    }

    @Test
    @DisplayName("403 이면 업종별 활용신청 안내를 담아 실패한다")
    void explainsForbiddenAsMissingDatasetSubscription() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.FORBIDDEN));

        assertThatThrownBy(() -> adapter("test-key", 100).collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("활용신청");
    }

    @Test
    @DisplayName("서버 오류는 재시도 후에도 실패로 끝난다")
    void failsAfterRetryOnServerError() {
        server.expect(requestTo(url(1, 100))).andRespond(withServerError());
        server.expect(requestTo(url(1, 100))).andRespond(withServerError());

        LocalDataApiAdapter adapter = new LocalDataApiAdapter(restClient, new ObjectMapper(), masterCatalog,
                BASE_URL, "test-key", 100, 10, 0, 9000, 2, 0, 2, 0,
                new ExternalApiMetrics(METER_REGISTRY), new CallBudgetMetrics(METER_REGISTRY));

        assertThatThrownBy(() -> adapter.collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataApiException.class);
        server.verify();
    }

    @Test
    @DisplayName("일일 호출 예산을 넘으면 예산 소진 전용 예외로 알려 수집 Step 이 정상 종료할 수 있게 한다")
    void signalsBudgetExhaustionWithDedicatedException() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(0), MediaType.APPLICATION_JSON));

        // 예산 1회. 첫 수집은 통과하고 두 번째는 호출 전에 막힌다.
        LocalDataApiAdapter adapter = new LocalDataApiAdapter(restClient, new ObjectMapper(), masterCatalog,
                BASE_URL, "test-key", 100, 10, 0, 1, 1, 0, 2, 0,
                new ExternalApiMetrics(METER_REGISTRY), new CallBudgetMetrics(METER_REGISTRY));

        adapter.collect(RESTAURANT, JONGNO);
        assertThat(adapter.callsUsed()).isEqualTo(1);
        assertThat(adapter.hasRemainingCapacity()).isFalse();

        assertThatThrownBy(() -> adapter.collect(RESTAURANT, JONGNO))
                .isInstanceOf(LocalDataCallBudgetExceededException.class)
                .hasMessageContaining("일일 호출 예산 소진");
        server.verify();
    }

    @Test
    @DisplayName("호출 예산은 날짜가 바뀌면 리셋돼 다음 날 수집이 이어진다")
    void resetsCallBudgetWhenDayChanges() {
        // given — 예산 1회짜리 어댑터가 오늘 예산을 다 썼다
        LocalDataApiAdapter adapter = new LocalDataApiAdapter(restClient, new ObjectMapper(), masterCatalog,
                BASE_URL, "test-key", 100, 10, 0, 1, 1, 0, 2, 0,
                new ExternalApiMetrics(METER_REGISTRY), new CallBudgetMetrics(METER_REGISTRY));
        LocalDate day1 = LocalDate.of(2026, 8, 19);

        assertThat(adapter.reserveCall(day1)).isTrue();
        assertThat(adapter.reserveCall(day1)).isFalse();

        // when — 날짜가 바뀐다
        LocalDate day2 = day1.plusDays(1);

        // then — 프로세스가 계속 떠 있어도 다음 날 예산이 살아난다
        assertThat(adapter.reserveCall(day2)).isTrue();
        assertThat(adapter.reserveCall(day2)).isFalse();
    }

    @Test
    @DisplayName("업종 마스터에 없는 업종은 호출하지 않는다")
    void rejectsIndustryMissingFromMaster() {
        given(masterCatalog.industryMaster()).willReturn(IndustryMaster.empty());

        assertThatThrownBy(() -> adapter("test-key", 100).collect(IndustryCode.of("UNKNOWN"), JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("slug");
    }

    // ------------------------------------------------------------------ 비밀값

    @Test
    @DisplayName("로그에 남길 URL 의 serviceKey 는 마스킹된다")
    void masksServiceKeyInUrl() {
        String masked = LocalDataApiAdapter.mask(
                "http://x/1741000/a/info?serviceKey=SUPER-SECRET&pageNo=1");

        assertThat(masked).doesNotContain("SUPER-SECRET");
        assertThat(masked).contains("serviceKey=****");
        assertThat(masked).contains("pageNo=1");
    }

    @Test
    @DisplayName("429 응답의 Retry-After 를 실제 응답에서 읽어 그만큼 기다린 뒤 재시도한다")
    void waitsForRetryAfterHeaderFromActualResponseBeforeRetrying() {
        // given - 첫 호출은 Retry-After: 1 을 단 429, 두 번째는 정상
        server.expect(requestTo(url(1, 100)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS)
                        .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "1"));
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(1, item("R-1", "01", "3000000")), MediaType.APPLICATION_JSON));

        // 백오프 기본 지연은 0이다. 그래도 1초를 기다렸다면 대기값의 출처는 응답 헤더뿐이다.
        LocalDataApiAdapter adapter = new LocalDataApiAdapter(restClient, new ObjectMapper(), masterCatalog,
                BASE_URL, "test-key", 100, 10, 0, 9000, 2, 0, 2, 60_000,
                new ExternalApiMetrics(METER_REGISTRY), new CallBudgetMetrics(METER_REGISTRY));

        // when
        long startedAt = System.nanoTime();
        FacilityCollection collection = adapter.collect(RESTAURANT, JONGNO);
        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;

        // then
        assertThat(collection.readCount()).isEqualTo(1);
        assertThat(elapsedMs).isGreaterThanOrEqualTo(900L);
        server.verify();
    }

    @Test
    @DisplayName("Retry-After 헤더의 초 값을 밀리초로 읽는다")
    void readsRetryAfterHeaderInSeconds() {
        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.set(org.springframework.http.HttpHeaders.RETRY_AFTER, "3");

        assertThat(LocalDataApiAdapter.retryAfterMillis(headers)).contains(3000L);
        assertThat(LocalDataApiAdapter.retryAfterMillis(new org.springframework.http.HttpHeaders()))
                .isEqualTo(Optional.empty());
    }

    // ------------------------------------------------------------------ 재시도 지연

    @Test
    @DisplayName("재시도 지연이 시도마다 배수만큼 늘어난다 - 1초 → 2초 → 4초")
    void growsRetryDelayExponentiallyPerAttempt() {
        // given / when / then - 실제로 sleep 하지 않는 순수 계산이라 테스트가 느려지지 않는다
        assertThat(LocalDataApiAdapter.backoffDelayMs(1000, 2, 1, 60_000)).isEqualTo(1000);
        assertThat(LocalDataApiAdapter.backoffDelayMs(1000, 2, 2, 60_000)).isEqualTo(2000);
        assertThat(LocalDataApiAdapter.backoffDelayMs(1000, 2, 3, 60_000)).isEqualTo(4000);
    }

    @Test
    @DisplayName("지연은 max-retry-after-ms 상한을 넘지 않는다")
    void capsRetryDelayAtConfiguredMaximum() {
        assertThat(LocalDataApiAdapter.backoffDelayMs(1000, 2, 20, 60_000)).isEqualTo(60_000);
        assertThat(LocalDataApiAdapter.backoffDelayMs(1000, 2, 7, 10_000)).isEqualTo(10_000);
    }

    @Test
    @DisplayName("기본 지연이 0이거나 배수가 1 미만이면 지연이 늘어나지 않는다")
    void keepsDelayFlatForZeroBaseOrInvalidMultiplier() {
        assertThat(LocalDataApiAdapter.backoffDelayMs(0, 2, 3, 60_000)).isZero();
        assertThat(LocalDataApiAdapter.backoffDelayMs(1000, 0.5, 3, 60_000)).isEqualTo(1000);
    }

    @Test
    @DisplayName("어댑터 설정값으로 계산한 지연도 같은 규칙을 따른다")
    void computesBackoffFromAdapterConfiguration() {
        LocalDataApiAdapter adapter = new LocalDataApiAdapter(restClient, new ObjectMapper(), masterCatalog,
                BASE_URL, "test-key", 100, 10, 0, 9000, 3, 1000, 2, 60_000,
                new ExternalApiMetrics(METER_REGISTRY), new CallBudgetMetrics(METER_REGISTRY));

        assertThat(adapter.backoffDelayMs(1)).isEqualTo(1000);
        assertThat(adapter.backoffDelayMs(2)).isEqualTo(2000);
        assertThat(adapter.backoffDelayMs(3)).isEqualTo(4000);
    }

    @Test
    @DisplayName("numOfRows 는 공식 상한 100 을 넘지 않는다")
    void clampsPageSizeToOfficialMaximum() {
        server.expect(requestTo(url(1, 100)))
                .andRespond(withSuccess(body(0), MediaType.APPLICATION_JSON));

        adapter("test-key", 5000).collect(RESTAURANT, JONGNO);

        server.verify();
    }
}
