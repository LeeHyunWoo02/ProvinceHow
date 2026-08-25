package SDD.smash.domain.job.infrastructure.cache;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.IndustryShare;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import SDD.smash.domain.job.domain.port.RegionJobProfileCache;
import SDD.smash.global.metrics.CacheMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>키는 {@code job:profile:{시군구코드}}, 값은 {@link RegionJobProfilePayload}(JSON)다.
 * 프로필은 변화가 느리고 원본이 외부 공급자(사람인)라 무효화 시점이 없어 TTL 로만 신선도를 유지한다
 * (redis-conventions §5). 사람인 1일 500회 제한을 이 캐시로 흡수한다.
 *
 * <h2>네거티브 캐싱(규약 예외)</h2>
 * redis-conventions §3-4 는 "빈 결과 비캐싱"이지만, 표본 0건 프로필({@code sampleSize=0})도 짧은
 * TTL(negative-ttl, 설정값)로 캐싱한다 — 실제 호출로 0건이 나온 지역을 매번 재호출하면 500회/일을
 * 갉아먹기 때문이다. 정상 프로필은 긴 TTL(12h)로 저장해 구분한다. "키 존재=히트, 부재=미스".
 * ※ 유스케이스는 <b>실제 표본 조회를 시도한 경우</b>에만 put 을 부른다(미시도는 캐싱 안 함).
 */
@Component
@Slf4j
public class RegionJobProfileRedisAdapter implements RegionJobProfileCache {

    private static final String KEY_PREFIX = "job:profile:";

    /** 메트릭의 cache 태그 값. Redis 키 네임스페이스와 같게 둬서 지표와 키를 바로 대조한다. */
    private static final String CACHE_NAME = "job:profile";

    /** 정상(표본이 있는) 프로필 TTL. 프로필은 변화가 느리므로 반나절이면 충분하다. */
    private static final Duration POSITIVE_TTL = Duration.ofHours(12);

    private static final String SCAN_PATTERN = KEY_PREFIX + "*";
    private static final int SCAN_COUNT = 500;

    private final RedisTemplate<String, RegionJobProfilePayload> regionJobProfileRedisTemplate;

    /** 히트/미스/에러 계측. 캐시 동작 자체에는 관여하지 않는다. */
    private final CacheMetrics cacheMetrics;

    /** 빈 프로필(표본 0건) 캐싱 TTL. 짧게 잡아 데이터가 생기면 곧 재조회되게 한다. 설정값. */
    private final Duration negativeTtl;

    public RegionJobProfileRedisAdapter(
            RedisTemplate<String, RegionJobProfilePayload> regionJobProfileRedisTemplate,
            @Value("${apis.saramin.profile.negative-ttl:PT30M}") Duration negativeTtl,
            CacheMetrics cacheMetrics) {
        this.regionJobProfileRedisTemplate = regionJobProfileRedisTemplate;
        this.negativeTtl = negativeTtl;
        this.cacheMetrics = cacheMetrics;
    }

    @Override
    public Optional<RegionJobProfile> find(SigunguCode region) {
        String redisKey = redisKey(region);
        try {
            RegionJobProfilePayload payload = regionJobProfileRedisTemplate.opsForValue().get(redisKey);
            if (payload == null) {
                cacheMetrics.miss(CACHE_NAME);
                return Optional.empty();   // 키 부재 = 미스. 키 존재는 빈 프로필이어도 히트.
            }
            cacheMetrics.hit(CACHE_NAME);
            return Optional.of(toDomain(region, payload));
        } catch (RuntimeException e) {
            log.warn("[cache] 지역 채용 프로필 조회 실패 key={} - 미스로 처리", redisKey, e);
            cacheMetrics.error(CACHE_NAME);
            return Optional.empty();
        }
    }

    @Override
    public void put(RegionJobProfile profile) {
        if (profile == null) {
            return;
        }
        String redisKey = redisKey(profile.region());
        Duration ttl = profile.isEmpty() ? negativeTtl : POSITIVE_TTL;   // 빈 프로필은 짧게(네거티브)
        try {
            regionJobProfileRedisTemplate.opsForValue().set(redisKey, toPayload(profile), ttl);
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
