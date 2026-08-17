package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.domain.port.JobVacancyProvider;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobVacancyRaw;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * 사람인 채용정보 오픈 API 로 <b>개별 공고 카드 목록</b>을 가져오는 어댑터.
 * {@link JobVacancyProvider} 포트 구현이다. 집계용 {@link SaraminJobPostingApiAdapter} 와
 * 같은 사람인 클라이언트/스펙을 재사용하되, 지역 단건 조회 + 풍부 필드 파싱에 특화했다.
 *
 * <h2>지역 필터(정직하게 처리)</h2>
 * 사람인 {@code loc_cd} 는 우리 {@link SigunguCode}(행정표준 5자리)와 체계가 다르다.
 * 스펙 JSON 의 {@code mapping.regionCodes}(사람인 loc_cd → 우리 코드)를 <b>역방향</b>으로 써서
 * 우리 코드 → 사람인 loc_cd 를 찾는다. <b>역매핑을 찾지 못하면 loc_cd 없이 호출하지 않고
 * 빈 목록을 돌려준다</b> — 전국 공고를 특정 지역 공고인 척 내려주면 안 되기 때문이다.
 * 매핑표가 채워지면 자동으로 지역 필터가 동작한다.
 *
 * <h2>장애/미설정</h2>
 * access-key 가 비어 있으면 호출하지 않고 빈 목록. 호출/파싱이 실패해도(표시용 부가 기능이므로)
 * 예외를 삼키고 빈 목록을 돌려준다. 로그의 URL 은 access-key 를 가린다.
 *
 * <p>이 기능은 사람인 전용이라 provider 스위치와 무관하게 항상 등록된다. worknet 모드에서도
 * access-key 가 없으면 빈 목록이라 무해하다.
 */
@Component
@Slf4j
public class SaraminJobVacancyAdapter implements JobVacancyProvider {

    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private static final String ACTIVE_TRUE = "1";

    private final RestTemplate restTemplate;
    private final SaraminJobVacancyParser parser;
    private final SaraminApiSpecLoader specLoader;
    private final SaraminLocCodeResolver locCodeResolver;

    private final String baseUrl;
    private final String path;
    private final String accessKey;

    public SaraminJobVacancyAdapter(
            RestTemplate restTemplate,
            SaraminJobVacancyParser parser,
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
    public Optional<List<JobVacancy>> findVacancies(SigunguCode region, int size) {
        if (accessKey.isEmpty()) {
            log.warn("[saramin] access-key 가 비어 있어 채용공고 목록을 조회하지 않는다. region={} - 미시도(캐싱 안 함)",
                    region.value());
            return Optional.empty();
        }

        SaraminApiSpecFile spec = specLoader.spec();
        String locCd = locCodeResolver.resolve(region);
        if (locCd == null) {
            log.warn("[saramin] 지역 역매핑이 없어(사람인 loc_cd 미상) 채용공고 목록을 조회하지 않는다. "
                            + "region={} - 미시도. 매핑표(saramin-job-api.json mapping.regionCodes)를 채워야 동작한다.",
                    region.value());
            return Optional.empty();
        }

        int count = Math.min(Math.max(1, size), spec.request().maxCount());
        URI uri = buildUri(spec.request(), locCd, count);

        try {
            ResponseEntity<String> response = restTemplate.getForEntity(uri, String.class);
            List<SaraminJobVacancyRaw> raws = parser.parse(response.getBody(), spec.response());
            List<JobVacancy> vacancies = raws.stream()
                    .map(this::toVacancy)
                    .filter(java.util.Objects::nonNull)
                    .toList();
            log.debug("[saramin] 채용공고 목록 조회 region={}, locCd={}, raw={}건, 카드={}건",
                    region.value(), locCd, raws.size(), vacancies.size());
            // 실제 조회 성공(0건이어도) → 결과를 담아 돌려준다(유스케이스가 네거티브 캐싱).
            return Optional.of(vacancies);
        } catch (RuntimeException e) {
            // 호출 실패는 '미시도'로 취급 -> 네거티브 캐싱하지 않아 다음 요청에서 재시도한다.
            log.warn("[saramin] 채용공고 목록 조회 실패 region={}, url={} - 미시도(캐싱 안 함)",
                    region.value(), maskedUrl(uri), e);
            return Optional.empty();
        }
    }

    private JobVacancy toVacancy(SaraminJobVacancyRaw raw) {
        try {
            JobPostingId id = JobPostingId.of(raw.id());
            return new JobVacancy(
                    id,
                    raw.title(),
                    raw.companyName(),
                    raw.detailUrl(),
                    raw.regionName(),
                    raw.jobName(),
                    raw.salaryText(),
                    raw.experienceText(),
                    raw.educationText(),
                    raw.employmentType(),
                    ACTIVE_TRUE.equals(raw.active()),
                    toDate(raw.postingTimestamp()),
                    toDate(raw.expirationTimestamp()));
        } catch (DomainException e) {
            // 식별자/제목이 없는 공고는 카드로 만들 수 없어 건너뛴다.
            log.debug("[saramin] 불완전한 공고를 건너뛴다. id={}", raw.id());
            return null;
        }
    }

    /** epoch seconds 문자열 → 서울 기준 날짜. 값이 없거나 숫자가 아니면 {@code null}. */
    private LocalDate toDate(String epochSeconds) {
        if (epochSeconds == null || epochSeconds.isBlank()) {
            return null;
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(epochSeconds.trim())).atZone(SEOUL).toLocalDate();
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

    /** 로그용 URL. access-key 값을 가린다. */
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
