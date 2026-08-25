package SDD.smash.domain.address.infrastructure.external;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.global.metrics.ExternalApiMetrics;
import SDD.smash.domain.address.domain.port.PopulationSnapshotProvider;
import SDD.smash.domain.address.infrastructure.external.dto.KosisPopulationRow;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static SDD.smash.global.util.BatchTextUtil.isBlank;

/**
 * KOSIS 공유서비스 OpenAPI 어댑터. {@link PopulationSnapshotProvider} 포트 구현이다.
 *
 * <p><b>확인된 스펙</b>(docs/external-api-spec.md §1.1)
 * <ul>
 *   <li>통계표 지정 방식 {@code /Param/statisticsParameterData.do?method=getList} 를 쓴다.
 *       사용자 등록 방식({@code statisticsData.do})은 사람이 웹에서 통계표를 미리 등록해야 해 배치에 맞지 않는다.</li>
 *   <li>HTTPS 필수. HTTP 제공은 종료됐다.</li>
 *   <li><b>페이지 파라미터가 없다.</b> 조회량은 시점 범위와 분류 지정으로만 조절한다.
 *       그래서 이 어댑터는 <b>기준월 1개 = 요청 1회</b>로 나눠 부른다. 한 달치는 시군구 약 250건이라
 *       응답이 작고, 메모리에 쌓이는 것은 항상 한 달치뿐이다.</li>
 *   <li>오류도 <b>HTTP 200</b> 으로 온다. 본문 {@code {"err":"11","errMsg":"..."}} 를 보고 판정해야 한다.</li>
 * </ul>
 *
 * <p><b>미확인이라 설정으로 뺀 것</b>
 * <ul>
 *   <li>{@code jsonVD} — 공식 파라미터 표에 없고 예제 코드에만 나온다. 의미 미확인이라
 *       {@code apis.kosis.json-vd} 가 비어 있으면 <b>아예 붙이지 않는다</b>.</li>
 *   <li>시군구 5자리 코드({@code C1}) 는 2차 출처까지만 확인됐다. 그래서 코드 자릿수만 믿지 않고
 *       {@code sigungu} 테이블 대조를 1차 방어선으로 둔다(애플리케이션 계층).</li>
 *   <li>분당/일일 호출 제한 수치 미확인. 그래서 호출을 최소화한다 — 정상 경로는 <b>1회</b>다.</li>
 * </ul>
 *
 * <p><b>인증키를 로그에 남기지 않는다.</b> URL 을 남길 때는 {@link #maskApiKey(String)} 를 거친다.
 * 응답 본문 전체도 남기지 않는다.
 */
@Component
@Slf4j
public class KosisPopulationApiAdapter implements PopulationSnapshotProvider {

    private static final String PATH_SEGMENT_1 = "Param";
    private static final String PATH_SEGMENT_2 = "statisticsParameterData.do";
    private static final String METHOD_GET_LIST = "getList";
    private static final String FORMAT_JSON = "json";
    /** 분류1 전체. 전국·시도·시군구가 한 번에 온다 — 시군구만 남기는 것은 매퍼의 몫이다. */
    private static final String OBJ_L1_ALL = "ALL";

    private static final DateTimeFormatter PRD_DE_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

    private static final String ERR_FIELD = "err";
    private static final String ERR_MSG_FIELD = "errMsg";

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    private final String baseUrl;
    private final String apiKey;
    private final String orgId;
    private final String tableId;
    private final String itemId;
    private final String periodCode;
    private final String jsonVd;
    private final int maxAttempts;
    private final long retryBackoffMillis;
    private final int fallbackMonths;

    /** 호출 성공/실패 계측. 재시도 1회 = HTTP 호출 1회 단위로 센다. */
    private final ExternalApiMetrics externalApiMetrics;

    /** 메트릭의 api 태그 값. */
    private static final String API_NAME = "kosis";

    public KosisPopulationApiAdapter(
            RestTemplate restTemplate,
            ObjectMapper objectMapper,
            @Value("${apis.kosis.base-url:}") String baseUrl,
            @Value("${apis.kosis.api-key:}") String apiKey,
            @Value("${apis.kosis.org-id:}") String orgId,
            @Value("${apis.kosis.table-id:}") String tableId,
            @Value("${apis.kosis.item-id:}") String itemId,
            @Value("${apis.kosis.period-code:M}") String periodCode,
            @Value("${apis.kosis.json-vd:}") String jsonVd,
            @Value("${apis.kosis.retry-max-attempts:3}") int maxAttempts,
            @Value("${apis.kosis.retry-backoff-millis:1000}") long retryBackoffMillis,
            @Value("${apis.kosis.fallback-months:3}") int fallbackMonths,
            ExternalApiMetrics externalApiMetrics) {
        this.externalApiMetrics = externalApiMetrics;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.orgId = orgId;
        this.tableId = tableId;
        this.itemId = itemId;
        this.periodCode = periodCode;
        this.jsonVd = jsonVd;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryBackoffMillis = Math.max(0, retryBackoffMillis);
        this.fallbackMonths = Math.max(0, fallbackMonths);
    }

    /** 필수 설정이 하나라도 비어 있으면 호출하지 않는다. 빈 인증키로 KOSIS 를 두드리지 않기 위한 관문이다. */
    @Override
    public boolean isAvailable() {
        return !isBlank(baseUrl)
                && !isBlank(apiKey)
                && !isBlank(orgId)
                && !isBlank(tableId)
                && !isBlank(itemId);
    }

    @Override
    public List<PopulationSnapshot> fetch(YearMonth statisticsMonth) {
        requireAvailable();
        if (statisticsMonth == null) return List.of();

        long startedAt = System.currentTimeMillis();
        List<PopulationSnapshot> snapshots = call(periodUri(statisticsMonth), statisticsMonth.toString());
        List<PopulationSnapshot> sameMonth = onlyMonth(snapshots, statisticsMonth);

        log.info("[KOSIS] 인구 조회 완료 statisticsMonth={}, apiCalls=1, loaded={}, elapsed={}ms",
                statisticsMonth, sameMonth.size(), System.currentTimeMillis() - startedAt);
        return sameMonth;
    }

    /**
     * 최신 확정 월을 먼저 물어보고, 그것이 {@code notAfter} 보다 미래면 {@code notAfter} 부터 한 달씩 내려간다.
     *
     * <p>정상 경로는 <b>호출 1회</b>다 — {@code newEstPrdCnt=1} 이 최신 확정 시점의 자료를 그대로 준다.
     */
    @Override
    public List<PopulationSnapshot> fetchLatestNotAfter(YearMonth notAfter) {
        requireAvailable();
        if (notAfter == null) return List.of();

        long startedAt = System.currentTimeMillis();
        int apiCalls = 1;

        List<PopulationSnapshot> latest = call(latestUri(), "newEstPrdCnt=1");
        Optional<YearMonth> latestMonth = maxMonth(latest);

        if (latestMonth.isPresent() && !latestMonth.get().isAfter(notAfter)) {
            List<PopulationSnapshot> result = onlyMonth(latest, latestMonth.get());
            log.info("[KOSIS] 인구 조회 완료 baseMonth={}, statisticsMonth={}, apiCalls={}, loaded={}, elapsed={}ms",
                    notAfter, latestMonth.get(), apiCalls, result.size(), System.currentTimeMillis() - startedAt);
            return result;
        }

        log.warn("[KOSIS] 최신 확정 월({})이 기준월({})보다 뒤이거나 비어 있다 - 직전 확정 월로 되짚는다",
                latestMonth.map(YearMonth::toString).orElse("없음"), notAfter);

        YearMonth candidate = notAfter;
        for (int i = 0; i <= fallbackMonths; i++) {
            List<PopulationSnapshot> snapshots = onlyMonth(call(periodUri(candidate), candidate.toString()), candidate);
            apiCalls++;
            if (!snapshots.isEmpty()) {
                log.info("[KOSIS] 인구 조회 완료(fallback) baseMonth={}, statisticsMonth={}, apiCalls={}, "
                                + "loaded={}, elapsed={}ms",
                        notAfter, candidate, apiCalls, snapshots.size(), System.currentTimeMillis() - startedAt);
                return snapshots;
            }
            candidate = candidate.minusMonths(1);
        }

        log.warn("[KOSIS] 기준월 {} 이하 {}개월에서 확정 자료를 찾지 못했다. apiCalls={}, elapsed={}ms",
                notAfter, fallbackMonths + 1, apiCalls, System.currentTimeMillis() - startedAt);
        return List.of();
    }

    // --- 호출 -------------------------------------------------------------

    private void requireAvailable() {
        if (!isAvailable()) {
            throw new KosisApiException(
                    "KOSIS 설정이 비어 있어 호출하지 않는다. 확인 대상 키: "
                            + "apis.kosis.base-url / api-key / org-id / table-id / item-id");
        }
    }

    /** 재시도까지 포함한 1회 논리 호출. 실패는 {@link KosisApiException} 으로 번역해 던진다. */
    private List<PopulationSnapshot> call(URI uri, String period) {
        RuntimeException last = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String body = restTemplate.getForObject(uri, String.class);
                List<PopulationSnapshot> snapshots = toSnapshots(body, period);
                externalApiMetrics.success(API_NAME);
                return snapshots;
            } catch (KosisApiException e) {
                // 응답 파싱/판정 실패. 다시 불러도 같은 결과라 재시도하지 않고 그대로 올린다.
                externalApiMetrics.failure(API_NAME);
                throw e;
            } catch (org.springframework.web.client.HttpStatusCodeException e) {
                if (!isRetryable(e.getStatusCode())) {
                    externalApiMetrics.failure(API_NAME);
                    throw new KosisApiException(
                            "KOSIS 응답 상태 오류 status=" + e.getStatusCode().value() + ", period=" + period, e);
                }
                last = e;
            } catch (ResourceAccessException e) {
                // 커넥션/읽기 타임아웃(SocketTimeoutException)은 RestTemplate 이 여기로 감싸 던진다.
                last = new KosisApiException("KOSIS 연결 실패 period=" + period, e);
            } catch (RestClientException e) {
                last = new KosisApiException("KOSIS 호출 실패 period=" + period, e);
            }

            externalApiMetrics.failure(API_NAME);
            log.warn("[KOSIS] 호출 실패 period={}, attempt={}/{}, url={}",
                    period, attempt, maxAttempts, maskApiKey(uri.toString()));
            if (attempt < maxAttempts) {
                sleep();
            }
        }
        throw new KosisApiException("KOSIS 호출이 " + maxAttempts + "회 모두 실패했다. period=" + period, last);
    }

    private boolean isRetryable(HttpStatusCode status) {
        return status.is5xxServerError() || status.value() == HttpStatus.TOO_MANY_REQUESTS.value();
    }

    private void sleep() {
        if (retryBackoffMillis == 0) return;
        try {
            Thread.sleep(retryBackoffMillis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new KosisApiException("KOSIS 재시도 대기 중 인터럽트", ie);
        }
    }

    // --- 파싱 -------------------------------------------------------------

    /**
     * 응답 본문 → 시군구 인구 목록.
     *
     * <p>KOSIS 는 오류도 200 으로 주므로 <b>배열이 아닌 응답과 {@code err} 필드</b>를 먼저 본다.
     */
    private List<PopulationSnapshot> toSnapshots(String body, String period) {
        if (body == null || body.isBlank()) {
            log.warn("[KOSIS] 빈 응답 period={}", period);
            return List.of();
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body);
        } catch (Exception e) {
            throw new KosisApiException("KOSIS 응답을 JSON 으로 읽지 못했다. period=" + period, e);
        }

        JsonNode error = errorNode(root);
        if (error != null) {
            String code = error.path(ERR_FIELD).asText(null);
            String message = error.path(ERR_MSG_FIELD).asText("");
            throw new KosisApiException(
                    "KOSIS 오류 응답 err=" + code + ", errMsg=" + message + ", period=" + period, code, null);
        }

        if (!root.isArray()) {
            log.warn("[KOSIS] 배열이 아닌 응답 period={}, nodeType={}", period, root.getNodeType());
            return List.of();
        }
        if (root.isEmpty()) {
            log.warn("[KOSIS] 자료 없음 period={}", period);
            return List.of();
        }

        List<PopulationSnapshot> snapshots = new ArrayList<>();
        int notSigungu = 0;
        int otherItem = 0;
        int unparsable = 0;

        for (JsonNode node : root) {
            KosisPopulationRow row = KosisPopulationJsonMapper.toRow(node);

            if (!isTargetItem(row)) {
                otherItem++;
                continue;
            }
            if (KosisPopulationJsonMapper.normalizeSigunguCode(row.c1()) == null) {
                notSigungu++;   // 전국 합계(00) / 시도 합계(2자리) / 읍면동(7자리 이상)
                continue;
            }
            Optional<PopulationSnapshot> snapshot = KosisPopulationJsonMapper.toSnapshot(row);
            if (snapshot.isEmpty()) {
                unparsable++;
                continue;
            }
            snapshots.add(snapshot.get());
        }

        log.info("[KOSIS] 응답 파싱 period={}, read={}, sigungu={}, notSigungu={}, otherItem={}, unparsable={}",
                period, root.size(), snapshots.size(), notSigungu, otherItem, unparsable);
        return snapshots;
    }

    /** 항목(총인구수)이 다른 행은 대상이 아니다. 응답에 {@code ITM_ID} 가 없으면 통과시킨다. */
    private boolean isTargetItem(KosisPopulationRow row) {
        if (isBlank(row.itmId()) || isBlank(itemId)) return true;
        return itemId.trim().equalsIgnoreCase(row.itmId().trim());
    }

    /** 오류 본문이면 그 노드를, 아니면 {@code null} 을 돌려준다. 배열 안에 오류가 오는 경우도 본다. */
    private JsonNode errorNode(JsonNode root) {
        if (root.isObject() && root.hasNonNull(ERR_FIELD)) return root;
        if (root.isArray() && !root.isEmpty()) {
            JsonNode first = root.get(0);
            if (first.isObject() && first.hasNonNull(ERR_FIELD)) return first;
        }
        return null;
    }

    private List<PopulationSnapshot> onlyMonth(List<PopulationSnapshot> snapshots, YearMonth month) {
        return snapshots.stream().filter(s -> month.equals(s.statisticsMonth())).toList();
    }

    private Optional<YearMonth> maxMonth(List<PopulationSnapshot> snapshots) {
        return snapshots.stream().map(PopulationSnapshot::statisticsMonth).max(YearMonth::compareTo);
    }

    // --- URL --------------------------------------------------------------

    /** 최신 확정 시점 1개. {@code newEstPrdCnt} 와 {@code startPrdDe/endPrdDe} 는 택일이다. */
    private URI latestUri() {
        return build(builder -> builder.queryParam("newEstPrdCnt", 1));
    }

    private URI periodUri(YearMonth month) {
        String prdDe = month.format(PRD_DE_FORMAT);
        return build(builder -> builder
                .queryParam("startPrdDe", prdDe)
                .queryParam("endPrdDe", prdDe));
    }

    private URI build(java.util.function.UnaryOperator<UriComponentsBuilder> period) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl)
                .pathSegment(PATH_SEGMENT_1, PATH_SEGMENT_2)
                .queryParam("method", METHOD_GET_LIST)
                .queryParam("orgId", orgId)
                .queryParam("tblId", tableId)
                .queryParam("objL1", OBJ_L1_ALL)
                .queryParam("itmId", itemId)
                .queryParam("prdSe", periodCode)
                .queryParam("format", FORMAT_JSON);

        if (!isBlank(jsonVd)) {
            builder.queryParam("jsonVD", jsonVd);
        }
        builder = period.apply(builder);

        String key = apiKey.trim();
        if (looksEncoded(key)) {
            // 이미 인코딩된 키는 다시 인코딩하지 않는다(%2B → %252B 가 되는 것을 막는다).
            return URI.create(builder.build().encode(StandardCharsets.UTF_8).toUriString() + "&apiKey=" + key);
        }
        return URI.create(builder.queryParam("apiKey", key)
                .build().encode(StandardCharsets.UTF_8).toUriString());
    }

    private boolean looksEncoded(String key) {
        return key.contains("%2B") || key.contains("%2F") || key.contains("%3D");
    }

    /** 로그에 나가는 URL 의 {@code apiKey} 를 가린다. 키가 로그에 남는 것을 구조적으로 막는다. */
    static String maskApiKey(String url) {
        if (url == null) return null;
        return url.replaceAll("(?i)(apiKey=)[^&]*", "$1***");
    }
}
