package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.domain.port.JobVacancyCache;
import SDD.smash.global.metrics.CacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 지역별 채용공고 목록 캐시의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p>키는 {@code job:vacancy:{시군구코드}}, 값은 {@link JobVacancyListPayload}(JSON)다. 사람인
 * 1일 500회 제한 때문에 사용자 요청마다 직접 부르지 않고 이 캐시로 흡수한다. 한 지역에서 가져오는
 * 카드 수는 설정 상수라 키에는 지역만 넣는다(수가 바뀌면 TTL 안에서만 옛 결과가 남는다).
 *
 * <h2>네거티브 캐싱(규약 예외)</h2>
 * redis-conventions §3-4 는 "빈 결과 비캐싱"이지만, 여기서는 <b>의도적으로 빈 결과도 캐싱</b>한다.
 * 매핑표가 채워져 실제 사람인 호출이 발생하고, 0건 지역을 매 요청 재호출하면 500회/일을 갉아먹기
 * 때문이다. 대신 <b>짧은 TTL(negative-ttl, 설정값)</b>로 저장하고, 정상 결과는 긴 TTL 로 저장해
 * 둘을 구분한다. "키 존재 = 히트(빈 목록이면 캐시된 0건), 키 부재 = 미스"로 해석한다.
 * ※ 유스케이스는 <b>실제로 조회를 시도한 경우</b>에만 이 put 을 부른다(미시도는 캐싱 안 함).
 */
@Component
@Slf4j
public class JobVacancyRedisAdapter implements JobVacancyCache {

    private static final String KEY_PREFIX = "job:vacancy:";

    /** 메트릭의 cache 태그 값. Redis 키 네임스페이스와 같게 둬서 지표와 키를 바로 대조한다. */
    private static final String CACHE_NAME = "job:vacancy";

    /** 정상(비어있지 않은) 결과 TTL. 원본이 외부 공급자(사람인)라 무효화 시점이 없어 반나절로 둔다. */
    private static final Duration POSITIVE_TTL = Duration.ofHours(6);

    private static final String SCAN_PATTERN = KEY_PREFIX + "*";
    private static final int SCAN_COUNT = 500;

    private final RedisTemplate<String, JobVacancyListPayload> jobVacancyListRedisTemplate;

    /** 히트/미스/에러 계측. 캐시 동작 자체에는 관여하지 않는다. */
    private final CacheMetrics cacheMetrics;

    /** 빈 결과(0건) 캐싱 TTL. 짧게 잡아 데이터가 생기면 곧 재조회되게 한다. 설정값. */
    private final Duration negativeTtl;

    public JobVacancyRedisAdapter(
            RedisTemplate<String, JobVacancyListPayload> jobVacancyListRedisTemplate,
            @Value("${apis.saramin.vacancy.negative-ttl:PT30M}") Duration negativeTtl,
            CacheMetrics cacheMetrics) {
        this.jobVacancyListRedisTemplate = jobVacancyListRedisTemplate;
        this.negativeTtl = negativeTtl;
        this.cacheMetrics = cacheMetrics;
    }

    @Override
    public Optional<List<JobVacancy>> find(SigunguCode region) {
        String redisKey = redisKey(region);
        try {
            JobVacancyListPayload payload = jobVacancyListRedisTemplate.opsForValue().get(redisKey);
            if (payload == null) {
                cacheMetrics.miss(CACHE_NAME);
                return Optional.empty();   // 키 부재 = 미스
            }
            // 키 존재 = 히트. 빈 목록이면 "캐시된 0건"(네거티브 히트)이므로 Optional.of(빈 목록).
            // 네거티브 히트도 히트로 센다 - 사람인 호출을 막아준 것이 캐시의 성과다.
            cacheMetrics.hit(CACHE_NAME);
            List<JobVacancyPayload> vacancies = payload.getVacancies();
            if (vacancies == null || vacancies.isEmpty()) {
                return Optional.of(List.of());
            }
            return Optional.of(vacancies.stream().map(this::toDomain).toList());
        } catch (RuntimeException e) {
            log.warn("[cache] 채용공고 목록 조회 실패 key={} - 미스로 처리", redisKey, e);
            cacheMetrics.error(CACHE_NAME);
            return Optional.empty();
        }
    }

    @Override
    public void put(SigunguCode region, List<JobVacancy> vacancies) {
        String redisKey = redisKey(region);
        boolean negative = (vacancies == null || vacancies.isEmpty());
        Duration ttl = negative ? negativeTtl : POSITIVE_TTL;
        try {
            JobVacancyListPayload payload = new JobVacancyListPayload();
            payload.setVacancies(negative ? List.of() : vacancies.stream().map(this::toPayload).toList());
            jobVacancyListRedisTemplate.opsForValue().set(redisKey, payload, ttl);
        } catch (RuntimeException e) {
            log.warn("[cache] 채용공고 목록 저장 실패 key={}", redisKey, e);
        }
    }

    @Override
    public void evictAll() {
        try {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(SCAN_PATTERN).count(SCAN_COUNT).build();
            try (Cursor<String> cursor = jobVacancyListRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (keys.isEmpty()) {
                log.info("[cache] 삭제할 채용공고 목록 캐시 없음");
                return;
            }
            Long deleted = jobVacancyListRedisTemplate.delete(keys);
            log.info("[cache] 채용공고 목록 캐시 무효화 대상={}, 삭제={}", keys.size(), deleted);
        } catch (RuntimeException e) {
            log.warn("[cache] 채용공고 목록 캐시 무효화 실패", e);
        }
    }

    private String redisKey(SigunguCode region) {
        return KEY_PREFIX + region.value();
    }

    private JobVacancy toDomain(JobVacancyPayload p) {
        return new JobVacancy(
                JobPostingId.of(p.getPostingId()),
                p.getTitle(),
                p.getCompanyName(),
                p.getDetailUrl(),
                p.getRegionName(),
                p.getJobName(),
                p.getSalaryText(),
                p.getExperienceText(),
                p.getEducationText(),
                p.getEmploymentType(),
                p.isActive(),
                toDate(p.getPostingDate()),
                toDate(p.getExpirationDate()));
    }

    private JobVacancyPayload toPayload(JobVacancy v) {
        return new JobVacancyPayload(
                v.id().value(),
                v.title(),
                v.companyName(),
                v.detailUrl(),
                v.regionName(),
                v.jobName(),
                v.salaryText(),
                v.experienceText(),
                v.educationText(),
                v.employmentType(),
                v.active(),
                toText(v.postingDate()),
                toText(v.expirationDate()));
    }

    private String toText(LocalDate date) {
        return date == null ? null : date.toString();
    }

    private LocalDate toDate(String text) {
        return (text == null || text.isBlank()) ? null : LocalDate.parse(text);
    }
}
