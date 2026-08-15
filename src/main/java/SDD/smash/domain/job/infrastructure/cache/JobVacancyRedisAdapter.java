package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobPostingId;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.domain.port.JobVacancyCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
 * <p>키는 {@code job:vacancy:{시군구코드}}, 값은 {@link JobVacancyListPayload}(JSON), TTL 은 6시간이다.
 * 사람인 1일 500회 제한 때문에 사용자 요청마다 직접 부르지 않고 이 캐시로 흡수한다. 한 지역에서
 * 가져오는 카드 수는 설정 상수라 키에는 지역만 넣는다(수가 바뀌면 TTL 안에서만 옛 결과가 남는다).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JobVacancyRedisAdapter implements JobVacancyCache {

    private static final String KEY_PREFIX = "job:vacancy:";

    /** 원본이 외부 공급자(사람인)라 무효화 시점이 없다. 반나절보다 짧게 잡아 신선도를 유지한다. */
    private static final Duration TTL = Duration.ofHours(6);

    private static final String SCAN_PATTERN = KEY_PREFIX + "*";
    private static final int SCAN_COUNT = 500;

    private final RedisTemplate<String, JobVacancyListPayload> jobVacancyListRedisTemplate;

    @Override
    public Optional<List<JobVacancy>> find(SigunguCode region) {
        String redisKey = redisKey(region);
        try {
            JobVacancyListPayload payload = jobVacancyListRedisTemplate.opsForValue().get(redisKey);
            if (payload == null || payload.getVacancies() == null || payload.getVacancies().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(payload.getVacancies().stream().map(this::toDomain).toList());
        } catch (RuntimeException e) {
            log.warn("[cache] 채용공고 목록 조회 실패 key={} - 미스로 처리", redisKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(SigunguCode region, List<JobVacancy> vacancies) {
        if (vacancies == null || vacancies.isEmpty()) {
            return;   // 빈 결과는 캐싱하지 않는다(히트 판정이 "비어있지 않음"이라 대칭을 맞춘다).
        }
        String redisKey = redisKey(region);
        try {
            JobVacancyListPayload payload = new JobVacancyListPayload();
            payload.setVacancies(vacancies.stream().map(this::toPayload).toList());
            jobVacancyListRedisTemplate.opsForValue().set(redisKey, payload, TTL);
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
