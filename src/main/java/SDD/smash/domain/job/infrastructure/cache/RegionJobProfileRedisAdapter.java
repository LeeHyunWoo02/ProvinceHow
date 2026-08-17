package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.IndustryShare;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import SDD.smash.domain.job.domain.port.RegionJobProfileCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 지역 채용 프로필 캐시의 Redis 구현. {@code RedisTemplate} 은 이 클래스 밖으로 나가지 않는다.
 *
 * <p>키는 {@code job:profile:{시군구코드}}, 값은 {@link RegionJobProfilePayload}(JSON), TTL 12시간.
 * 프로필은 변화가 느리고 원본이 외부 공급자(사람인)라 무효화 시점이 없어 TTL 로만 신선도를 유지한다
 * (redis-conventions §5). 사람인 1일 500회 제한을 이 캐시로 흡수한다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RegionJobProfileRedisAdapter implements RegionJobProfileCache {

    private static final String KEY_PREFIX = "job:profile:";

    /** 프로필은 변화가 느리므로 반나절이면 충분하다. */
    private static final Duration TTL = Duration.ofHours(12);

    private static final String SCAN_PATTERN = KEY_PREFIX + "*";
    private static final int SCAN_COUNT = 500;

    private final RedisTemplate<String, RegionJobProfilePayload> regionJobProfileRedisTemplate;

    @Override
    public Optional<RegionJobProfile> find(SigunguCode region) {
        String redisKey = redisKey(region);
        try {
            RegionJobProfilePayload payload = regionJobProfileRedisTemplate.opsForValue().get(redisKey);
            if (payload == null) {
                return Optional.empty();
            }
            return Optional.of(toDomain(region, payload));
        } catch (RuntimeException e) {
            log.warn("[cache] 지역 채용 프로필 조회 실패 key={} - 미스로 처리", redisKey, e);
            return Optional.empty();
        }
    }

    @Override
    public void put(RegionJobProfile profile) {
        if (profile == null || profile.isEmpty()) {
            return;   // 표본이 없는 프로필은 캐싱하지 않는다.
        }
        String redisKey = redisKey(profile.region());
        try {
            regionJobProfileRedisTemplate.opsForValue().set(redisKey, toPayload(profile), TTL);
        } catch (RuntimeException e) {
            log.warn("[cache] 지역 채용 프로필 저장 실패 key={}", redisKey, e);
        }
    }

    @Override
    public void evictAll() {
        try {
            List<String> keys = new ArrayList<>();
            ScanOptions options = ScanOptions.scanOptions().match(SCAN_PATTERN).count(SCAN_COUNT).build();
            try (Cursor<String> cursor = regionJobProfileRedisTemplate.scan(options)) {
                while (cursor.hasNext()) {
                    keys.add(cursor.next());
                }
            }
            if (keys.isEmpty()) {
                log.info("[cache] 삭제할 지역 채용 프로필 캐시 없음");
                return;
            }
            Long deleted = regionJobProfileRedisTemplate.delete(keys);
            log.info("[cache] 지역 채용 프로필 캐시 무효화 대상={}, 삭제={}", keys.size(), deleted);
        } catch (RuntimeException e) {
            log.warn("[cache] 지역 채용 프로필 캐시 무효화 실패", e);
        }
    }

    private String redisKey(SigunguCode region) {
        return KEY_PREFIX + region.value();
    }

    private RegionJobProfile toDomain(SigunguCode region, RegionJobProfilePayload p) {
        List<IndustryShare> top = (p.getTopIndustries() == null) ? List.of()
                : p.getTopIndustries().stream()
                .map(i -> new IndustryShare(i.getName(), i.getCount()))
                .toList();
        return new RegionJobProfile(region, p.getSalaryMedianManwon(), p.getNewcomerRatio(),
                top, p.getSampleSize(), p.getSalaryParsedCount());
    }

    private RegionJobProfilePayload toPayload(RegionJobProfile profile) {
        List<RegionJobProfilePayload.IndustrySharePayload> top = profile.topIndustries().stream()
                .map(s -> new RegionJobProfilePayload.IndustrySharePayload(s.name(), s.count()))
                .toList();
        return new RegionJobProfilePayload(profile.salaryMedianManwon(), profile.newcomerRatio(),
                top, profile.sampleSize(), profile.salaryParsedCount());
    }
}
