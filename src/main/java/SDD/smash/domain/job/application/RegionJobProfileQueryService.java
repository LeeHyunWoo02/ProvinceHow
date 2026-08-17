package SDD.smash.domain.job.application;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.application.dto.RegionJobProfileView;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import SDD.smash.domain.job.domain.port.RegionJobProfileCache;
import SDD.smash.domain.job.domain.port.RegionJobProfileProvider;
import SDD.smash.domain.job.domain.service.RegionJobProfilePolicy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 지역 채용 프로필 조회 유스케이스. {@code recommendation} 이 지역 상세에서 이 Service 를 호출한다.
 *
 * <p>흐름: 캐시 확인 → (미스면) 단일비행 락 → 재확인 → 표본 조회 → 도메인 정책 집계 → 캐시 → DTO.
 * 집계는 {@link RegionJobProfilePolicy}(도메인)가 하고, 유스케이스는 오케스트레이션만 한다.
 *
 * <p><b>트랜잭션이 없다.</b> DB(JPA)를 건드리지 않고 캐시와 외부 API 만 쓴다.
 *
 * <h2>스탬피드 방지 / 네거티브 캐싱</h2>
 * {@link JobVacancyQueryService} 와 동일 원칙이다. 지역별 in-process 단일비행({@link KeyedLock})으로
 * 콜드 캐시 동시 호출을 한 번으로 접고, 락 타임아웃 시 캐시 재확인 후 빈 프로필로 degrade 한다.
 * 공급자가 실제 표본 조회를 시도해 0건이 나온 경우(빈 프로필)는 짧은 TTL 로 네거티브 캐싱하고,
 * 미시도({@code Optional.empty()})는 캐싱하지 않는다.
 */
@Service
@Slf4j
public class RegionJobProfileQueryService {

    private final RegionJobProfileProvider regionJobProfileProvider;
    private final RegionJobProfileCache regionJobProfileCache;
    private final RegionJobProfilePolicy policy = new RegionJobProfilePolicy();
    private final KeyedLock<SigunguCode> singleFlight = new KeyedLock<>();

    /** 프로필 집계에 쓸 표본 수. 사람인 500회/일 제한 때문에 1회 호출(<=110)로 끝낸다. */
    private final int sampleSize;
    /** 업종 구성 상위 N. */
    private final int topIndustries;
    /** 단일비행 락 대기 한도. 초과하면 빈 프로필로 degrade. */
    private final Duration lockTimeout;

    public RegionJobProfileQueryService(
            RegionJobProfileProvider regionJobProfileProvider,
            RegionJobProfileCache regionJobProfileCache,
            @Value("${apis.saramin.profile.sample-size:100}") int sampleSize,
            @Value("${apis.saramin.profile.top-industries:5}") int topIndustries,
            @Value("${apis.saramin.cache.lock-timeout:PT3S}") Duration lockTimeout) {
        this.regionJobProfileProvider = regionJobProfileProvider;
        this.regionJobProfileCache = regionJobProfileCache;
        this.sampleSize = Math.max(1, sampleSize);
        this.topIndustries = Math.max(1, topIndustries);
        this.lockTimeout = lockTimeout;
    }

    public RegionJobProfileView getProfile(SigunguCode region) {
        Optional<RegionJobProfile> cached = regionJobProfileCache.find(region);
        if (cached.isPresent()) {
            return RegionJobProfileView.from(cached.get());
        }

        ReentrantLock lock = singleFlight.forKey(region);
        boolean acquired;
        try {
            acquired = lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return RegionJobProfileView.from(RegionJobProfile.empty(region));
        }
        if (!acquired) {
            log.warn("[profile] 단일비행 락 대기 초과 region={} - 캐시 재확인 후 degrade", region.value());
            return regionJobProfileCache.find(region)
                    .map(RegionJobProfileView::from)
                    .orElseGet(() -> RegionJobProfileView.from(RegionJobProfile.empty(region)));
        }
        try {
            Optional<RegionJobProfile> recheck = regionJobProfileCache.find(region);   // 락 안에서 재확인
            if (recheck.isPresent()) {
                return RegionJobProfileView.from(recheck.get());
            }
            Optional<List<JobPostingSample>> sampled = regionJobProfileProvider.sample(region, sampleSize);
            if (sampled.isEmpty()) {
                // 미시도 → 캐싱하지 않고 빈 프로필 반환(키/매핑이 준비되면 즉시 동작).
                return RegionJobProfileView.from(RegionJobProfile.empty(region));
            }
            RegionJobProfile profile = policy.profile(region, sampled.get(), topIndustries);
            regionJobProfileCache.put(profile);   // 정상 or 네거티브(빈 프로필) 캐싱
            return RegionJobProfileView.from(profile);
        } finally {
            lock.unlock();
        }
    }
}
