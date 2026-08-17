package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.ExperienceLevel;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.domain.port.RegionJobProfileProvider;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobSampleRaw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 사람인 채용정보 오픈 API 로 <b>지역 채용 프로필용 표본</b>을 가져오는 어댑터.
 * {@link RegionJobProfileProvider} 포트 구현이다. 채용공고 목록 어댑터와 같은 사람인 클라이언트·
 * 지역 역매핑({@link SaraminLocCodeResolver})을 재사용한다.
 *
 * <h2>지역 필터(정직하게 처리)</h2>
 * 역매핑({@code SigunguCode → loc_cd})이 없으면(현재 상태) 사람인을 호출하지 않고 빈 표본을
 * 돌려준다 — 전국을 특정 지역인 척 집계하면 안 되기 때문이다. access-key 가 없어도 빈 표본.
 *
 * <h2>호출량</h2>
 * 1회 호출(count 최대 110)로 표본을 받는다(사람인 1일 500회 제한). sampleSize 가 110 을 넘으면
 * 110 으로 자른다.
 *
 * <h2>연봉 파싱의 한계</h2>
 * 사람인 {@code salary.name}("3,000~4,000만원", "회사내규에 따름" 등)에서 연봉 숫자 구간만 뽑는다.
 * <ul>
 *   <li>월급·주급·시급 표기("월","주","시급","시간당","일급")는 연봉이 아니므로 <b>제외</b>한다
 *       (만원 단위로 오해석하면 연봉 통계가 왜곡된다).</li>
 *   <li>"억" 표기는 만원으로 환산한다: "1억 2,000만원" → 12,000만원, "1억" → 10,000만원.
 *       단, 억이 섞인 <b>범위</b>("1억~2억")는 애매하므로 보수적으로 제외한다.</li>
 *   <li>순수 "n만원~m만원"·"n만원"은 그대로 [min,max] 만원.</li>
 *   <li>그 외(면접후결정·회사내규 등 숫자/단위 없음)는 조용히 제외.</li>
 * </ul>
 * 파싱 실패/제외는 표본에서 연봉만 비우고(경력·업종은 유지), 집계는 파싱 성공 수를 따로 센다(정직성).
 */
@Component
@Slf4j
public class SaraminRegionProfileAdapter implements RegionJobProfileProvider {

    /** salary.name 에서 숫자(콤마 포함)를 뽑는 패턴. */
    private static final Pattern NUMBER = Pattern.compile("[0-9][0-9,]*");
    /** 연봉이 아닌 급여 주기 표기. 하나라도 있으면 연봉 집계에서 제외한다. */
    private static final String[] NON_ANNUAL_MARKERS = {"월", "주", "시급", "시간당", "일급"};
    private static final String EOK_MARKER = "억";
    private static final String MANWON_MARKER = "만";
    private static final int EOK_IN_MANWON = 10_000;

    private final RestTemplate restTemplate;
    private final SaraminJobSampleParser parser;
    private final SaraminApiSpecLoader specLoader;
    private final SaraminLocCodeResolver locCodeResolver;

    private final String baseUrl;
    private final String path;
    private final String accessKey;

    public SaraminRegionProfileAdapter(
            RestTemplate restTemplate,
            SaraminJobSampleParser parser,
            SaraminApiSpecLoader specLoader,
            SaraminLocCodeResolver locCodeResolver,
            @Value("${apis.saramin.base-url:https://oapi.saramin.co.kr}") String baseUrl,
            @Value("${apis.saramin.path:/job-search}") String path,
            @Value("${apis.saramin.access-key:}") String accessKey) {
        this.restTemplate = restTemplate;
        this.parser = parser;
        this.specLoader = specLoader;
        this.locCodeResolver = locCodeResolver;
        this.baseUrl = baseUrl;
        this.path = path;
        this.accessKey = (accessKey == null) ? "" : accessKey.trim();
    }

    @Override
    public Optional<List<JobPostingSample>> sample(SigunguCode region, int sampleSize) {
        if (accessKey.isEmpty()) {
            log.warn("[saramin] access-key 가 비어 있어 지역 채용 프로필 표본을 조회하지 않는다. region={} - 미시도(캐싱 안 함)",
                    region.value());
            return Optional.empty();
        }

        SaraminApiSpecFile spec = specLoader.spec();
        String locCd = locCodeResolver.resolve(region);
        if (locCd == null) {
            log.warn("[saramin] 지역 역매핑이 없어(사람인 loc_cd 미상) 프로필 표본을 조회하지 않는다. "
                            + "region={} - 미시도. 매핑표(saramin-job-api.json mapping.regionCodes)를 채워야 동작한다.",
                    region.value());
            return Optional.empty();
        }

        int count = Math.min(Math.max(1, sampleSize), spec.request().maxCount());
        URI uri = buildUri(spec.request(), locCd, count);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            List<SaraminJobSampleRaw> raws = parser.parse(response.getBody(), spec.response());
            List<JobPostingSample> samples = raws.stream().map(this::toSample).toList();
            log.debug("[saramin] 프로필 표본 조회 region={}, locCd={}, 표본={}건", region.value(), locCd, samples.size());
            // 실제 조회 성공(0건이어도) → 표본을 담아 돌려준다(유스케이스가 네거티브 캐싱).
            return Optional.of(samples);
        } catch (RuntimeException e) {
            // 호출 실패는 '미시도'로 취급 -> 네거티브 캐싱하지 않아 다음 요청에서 재시도한다.
            log.warn("[saramin] 프로필 표본 조회 실패 region={}, url={} - 미시도(캐싱 안 함)", region.value(), maskedUrl(uri), e);
            return Optional.empty();
        }
    }

    private JobPostingSample toSample(SaraminJobSampleRaw raw) {
        int[] salaryRange = parseSalaryRange(raw.salaryName());
        Integer min = (salaryRange == null) ? null : salaryRange[0];
        Integer max = (salaryRange == null) ? null : salaryRange[1];
        ExperienceLevel experience = ExperienceLevel.fromCode(parseIntOrNull(raw.experienceCode()));
        return new JobPostingSample(min, max, experience, raw.industryName());
    }

    /**
     * salary.name 에서 [최소, 최대] 만원 구간을 뽑는다. 연봉으로 해석할 수 없으면 {@code null}(제외).
     * 규칙은 클래스 주석의 "연봉 파싱의 한계" 참조.
     */
    private int[] parseSalaryRange(String salaryName) {
        if (salaryName == null || salaryName.isBlank()) {
            return null;
        }
        for (String marker : NON_ANNUAL_MARKERS) {
            if (salaryName.contains(marker)) {
                return null;   // 월급·주급·시급 등은 연봉이 아니므로 제외
            }
        }

        boolean hasEok = salaryName.contains(EOK_MARKER);
        boolean hasMan = salaryName.contains(MANWON_MARKER);
        if (!hasEok && !hasMan) {
            return null;   // 금액 단위가 없으면(회사내규·면접후결정 등) 제외
        }

        if (hasEok) {
            // 억이 섞인 범위는 애매하므로 보수적으로 제외한다.
            if (salaryName.contains("~") || salaryName.contains("-")) {
                return null;
            }
            int eokIndex = salaryName.indexOf(EOK_MARKER);
            Integer eok = firstNumber(salaryName.substring(0, eokIndex));
            if (eok == null) {
                return null;
            }
            String afterEok = salaryName.substring(eokIndex + EOK_MARKER.length());
            int man = afterEok.contains(MANWON_MARKER) ? orZero(firstNumber(afterEok)) : 0;
            int amount = eok * EOK_IN_MANWON + man;
            return new int[]{amount, amount};
        }

        // 만원 단위만: 숫자 하나면 [n,n], 둘 이상이면 [첫째, 둘째]
        Integer first = null;
        Integer second = null;
        Matcher matcher = NUMBER.matcher(salaryName);
        while (matcher.find()) {
            int value = Integer.parseInt(matcher.group().replace(",", ""));
            if (first == null) {
                first = value;
            } else {
                second = value;
                break;
            }
        }
        if (first == null) {
            return null;
        }
        int max = (second == null) ? first : second;
        return new int[]{Math.min(first, max), Math.max(first, max)};
    }

    /** 문자열에서 첫 숫자(콤마 제거)를 뽑는다. 없으면 {@code null}. */
    private Integer firstNumber(String text) {
        if (text == null) {
            return null;
        }
        Matcher matcher = NUMBER.matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group().replace(",", "")) : null;
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private Integer parseIntOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private URI buildUri(SaraminApiSpecFile.Request request, String locCd, int count) {
        return UriComponentsBuilder.fromHttpUrl(baseUrl)
                .path(path)
                .queryParam(request.accessKeyParam(), accessKey)
                .queryParam("loc_cd", locCd)
                .queryParam(request.startParam(), 0)
                .queryParam(request.countParam(), count)
                .build().encode().toUri();
    }

    private String maskedUrl(URI uri) {
        String url = uri.toString();
        if (accessKey.isEmpty()) {
            return url;
        }
        String masked = url.replace(accessKey, "****");
        String encoded = UriUtils.encode(accessKey, StandardCharsets.UTF_8);
        return masked.replace(encoded, "****");
    }
}
