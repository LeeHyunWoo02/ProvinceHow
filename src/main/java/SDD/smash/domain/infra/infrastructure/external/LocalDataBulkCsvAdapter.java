package SDD.smash.domain.infra.infrastructure.external;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.port.InfraFacilityProvider;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;

/**
 * 무인증 벌크 CSV 어댑터. {@code InfraFacilityProvider} 포트의 <b>선택</b> 구현이다.
 *
 * <pre>
 * GET https://file.localdata.go.kr/file/download/{slug}/info?orgCode={7자리코드}
 * Referer: https://www.data.go.kr/          ← 없으면 302 → /error.html
 * </pre>
 *
 * <h2>왜 이 경로가 필요한가</h2>
 * 공식 API 는 {@code numOfRows} 상한 100 + 개발계정 10,000회/일이라 일반음식점 전국
 * 2,129,830건을 받으려면 21,299회가 필요하다. <b>하루 만에 불가능하다.</b>
 * 벌크 CSV 는 자치단체·업종당 <b>요청 1회</b>로 전량을 받는다(서울종로구 일반음식점 실측 20,558행).
 *
 * <h2>그럼에도 기본이 아닌 이유</h2>
 * 이 서버의 공식 유지 기간/SLA 가 <b>확인되지 않았다</b>. 공식 문서 없이 동작만 실측된 경로라
 * 기본값으로 삼으면 어느 날 조용히 멈춘다. 그래서 {@code infra.collect.source=BULK_CSV} 로
 * 명시적으로 켜야 쓰인다.
 *
 * <h2>형식</h2>
 * 인코딩 <b>CP949</b>, 헤더는 한글 컬럼명, 컬럼 집합은 업종마다 다르다.
 * 이 어댑터도 API 어댑터와 같은 값만 읽는다 — {@code 관리번호} / {@code 영업상태코드} /
 * {@code 개방자치단체코드} / {@code 지번주소} / {@code 도로명주소}.
 * 헤더 이름으로 위치를 찾으므로 컬럼 순서가 달라도 동작한다.
 *
 * <p>주소 두 컬럼은 <b>선택</b>이다. 없어도 실패시키지 않는다 — 일반구를 두지 않은 자치단체는
 * 개방자치단체코드만으로 시군구가 확정되기 때문이다. 일반구 시(수원·성남 등)에서 주소가 비면
 * 그 사업장은 조립 단계에서 매핑 실패로 집계된다.
 */
@Component
@Slf4j
public class LocalDataBulkCsvAdapter implements InfraFacilityProvider {

    static final String HEADER_MANAGEMENT_NO = "관리번호";
    static final String HEADER_STATUS_CODE = "영업상태코드";
    static final String HEADER_ORG_CODE = "개방자치단체코드";
    static final String HEADER_LOT_ADDRESS = "지번주소";
    static final String HEADER_ROAD_ADDRESS = "도로명주소";

    private static final Charset CP949 = Charset.forName("MS949");

    private final RestTemplate restTemplate;
    private final InfraMasterCatalog masterCatalog;

    private final String baseUrl;
    private final String referer;
    private final long requestIntervalMs;

    private final Object intervalLock = new Object();
    private long nextAllowedAtMillis;

    public LocalDataBulkCsvAdapter(
            RestTemplate restTemplate,
            InfraMasterCatalog masterCatalog,
            @Value("${apis.localdata.bulk-base-url:https://file.localdata.go.kr/file/download}") String baseUrl,
            @Value("${apis.localdata.bulk-referer:https://www.data.go.kr/}") String referer,
            @Value("${apis.localdata.bulk-request-interval-ms:500}") long requestIntervalMs) {
        this.restTemplate = restTemplate;
        this.masterCatalog = masterCatalog;
        this.baseUrl = baseUrl;
        this.referer = referer;
        this.requestIntervalMs = Math.max(0, requestIntervalMs);
    }

    @Override
    public boolean isReady() {
        // 인증키가 필요 없다. 대신 Referer 가 없으면 302 로 튕긴다.
        return referer != null && !referer.isBlank();
    }

    @Override
    public String readinessDescription() {
        return isReady()
                ? "벌크 CSV 경로 사용 가능(인증키 불필요, Referer 설정됨)"
                : "벌크 CSV 는 Referer 헤더가 필수다(apis.localdata.bulk-referer).";
    }

    /** 자치단체·업종당 요청 1회로 전량을 받는 경로라 호출 예산 개념이 없다. */
    @Override
    public boolean hasRemainingCapacity() {
        return true;
    }

    @Override
    public FacilityCollection collect(IndustryCode industryCode, LocalDataRegionCode regionCode) {
        if (!isReady()) {
            throw new LocalDataApiException("[localdata-bulk] " + readinessDescription());
        }
        String slug = slugOf(industryCode);
        URI uri = URI.create(trimTrailingSlash(baseUrl) + "/" + slug + "/info?orgCode=" + regionCode.value());

        HttpHeaders headers = new HttpHeaders();
        headers.set(HttpHeaders.REFERER, referer);

        waitForInterval();
        ResponseEntity<byte[]> response;
        try {
            response = restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), byte[].class);
        } catch (RuntimeException e) {
            throw new LocalDataApiException(String.format(
                    "[localdata-bulk] 다운로드 실패 slug=%s, org=%s", slug, regionCode.value()), e);
        }

        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            throw new LocalDataApiException(String.format(
                    "[localdata-bulk] 빈 응답 slug=%s, org=%s", slug, regionCode.value()));
        }

        List<InfraFacility> facilities = parse(new String(body, CP949), regionCode);
        FacilityCollection collection = FacilityCollection.of(facilities, 1);
        log.debug("[localdata-bulk] 수집 industry={}, org={}, bytes={}, read={}, dedup={}, operating={}",
                industryCode.value(), regionCode.value(), body.length, collection.readCount(),
                collection.duplicatesDropped(), collection.operatingCount());
        return collection;
    }

    /**
     * CP949 로 디코딩된 CSV 본문을 사업장 목록으로 바꾼다.
     *
     * <p>헤더 이름으로 컬럼 위치를 찾는다. 필수 컬럼이 없으면 <b>0건으로 삼키지 않고</b> 예외다 —
     * 형식이 바뀐 것을 "시설 없음"으로 오해하면 스냅샷이 조용히 망가진다.
     */
    List<InfraFacility> parse(String csv, LocalDataRegionCode fallbackOrgCode) {
        String[] lines = csv.split("\r?\n");
        if (lines.length == 0) {
            throw new LocalDataApiException("[localdata-bulk] CSV 가 비어 있다");
        }

        List<String> header = splitCsvLine(stripBom(lines[0]));
        int managementNoIndex = indexOf(header, HEADER_MANAGEMENT_NO);
        int statusIndex = indexOf(header, HEADER_STATUS_CODE);
        int orgCodeIndex = indexOf(header, HEADER_ORG_CODE);
        int lotAddressIndex = indexOf(header, HEADER_LOT_ADDRESS);
        int roadAddressIndex = indexOf(header, HEADER_ROAD_ADDRESS);

        if (managementNoIndex < 0 || statusIndex < 0) {
            throw new LocalDataApiException(
                    "[localdata-bulk] 필수 컬럼(관리번호/영업상태코드)을 찾지 못했다. CSV 형식이 바뀌었을 수 있다.");
        }

        List<InfraFacility> facilities = new ArrayList<>();
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i];
            if (line == null || line.isBlank()) {
                continue;
            }
            List<String> columns = splitCsvLine(line);
            String managementNo = at(columns, managementNoIndex);
            if (managementNo == null) {
                continue;
            }
            BusinessStatus status = BusinessStatus.fromCode(at(columns, statusIndex));

            LocalDataRegionCode orgCode = fallbackOrgCode;
            String rawOrgCode = at(columns, orgCodeIndex);
            if (rawOrgCode != null) {
                try {
                    orgCode = LocalDataRegionCode.of(rawOrgCode);
                } catch (RuntimeException e) {
                    orgCode = fallbackOrgCode;
                }
            }
            facilities.add(new InfraFacility(managementNo, status, orgCode,
                    at(columns, lotAddressIndex), at(columns, roadAddressIndex)));
        }
        return facilities;
    }

    // ------------------------------------------------------------------ 보조

    private String slugOf(IndustryCode industryCode) {
        return masterCatalog.industryMaster().byCode(industryCode)
                .map(IndustryMasterEntry::slug)
                .filter(slug -> !slug.isBlank())
                .orElseThrow(() -> new LocalDataApiException(
                        "[localdata-bulk] 업종 마스터에 slug 가 없다. industryCode=" + industryCode.value()));
    }

    private void waitForInterval() {
        if (requestIntervalMs <= 0) {
            return;
        }
        long waitMs;
        synchronized (intervalLock) {
            long now = System.currentTimeMillis();
            waitMs = Math.max(0, nextAllowedAtMillis - now);
            nextAllowedAtMillis = Math.max(now, nextAllowedAtMillis) + requestIntervalMs;
        }
        if (waitMs > 0) {
            try {
                Thread.sleep(waitMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new LocalDataApiException("[localdata-bulk] 대기 중 인터럽트", e);
            }
        }
    }

    /** 큰따옴표로 감싼 값 안의 콤마를 보존하는 최소 CSV 분리기. */
    static List<String> splitCsvLine(String line) {
        List<String> columns = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(ch);
            }
        }
        columns.add(current.toString());
        return columns;
    }

    private static int indexOf(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static String at(List<String> columns, int index) {
        if (index < 0 || index >= columns.size()) {
            return null;
        }
        String value = columns.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private static String stripBom(String line) {
        return (!line.isEmpty() && line.charAt(0) == '﻿') ? line.substring(1) : line;
    }

    private static String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
