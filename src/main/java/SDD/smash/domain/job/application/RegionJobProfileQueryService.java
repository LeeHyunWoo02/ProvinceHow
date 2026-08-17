package SDD.smash.domain.job.application;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.application.dto.RegionJobProfileView;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import SDD.smash.domain.job.domain.port.RegionJobProfileCache;
import SDD.smash.domain.job.domain.port.RegionJobProfileProvider;
import SDD.smash.domain.job.domain.service.RegionJobProfilePolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 지역 채용 프로필 조회 유스케이스. {@code recommendation} 이 지역 상세에서 이 Service 를 호출한다.
 *
 * <p>흐름: 캐시 확인 → (미스면) 표본 조회 → 도메인 정책으로 집계 → 캐시 적재 → 표시 DTO 변환.
 * 집계는 {@link RegionJobProfilePolicy}(도메인)가 하고, 유스케이스는 오케스트레이션만 한다.
 *
 * <p><b>트랜잭션이 없다.</b> DB(JPA)를 건드리지 않고 캐시와 외부 API 만 쓴다. 사람인 access-key 가
 * 없거나 지역 역매핑이 비어 있으면 공급자 어댑터가 빈 표본을 돌려주고, 프로필은 빈 값이 된다.
 */
@Service
public class RegionJobProfileQueryService {

    private final RegionJobProfileProvider regionJobProfileProvider;
    private final RegionJobProfileCache regionJobProfileCache;
    private final RegionJobProfilePolicy policy = new RegionJobProfilePolicy();

    /** 프로필 집계에 쓸 표본 수. 사람인 500회/일 제한 때문에 1회 호출(<=110)로 끝낸다. */
    private final int sampleSize;
    /** 업종 구성 상위 N. */
    private final int topIndustries;

    public RegionJobProfileQueryService(
            RegionJobProfileProvider regionJobProfileProvider,
            RegionJobProfileCache regionJobProfileCache,
            @Value("${apis.saramin.profile.sample-size:100}") int sampleSize,
            @Value("${apis.saramin.profile.top-industries:5}") int topIndustries) {
        this.regionJobProfileProvider = regionJobProfileProvider;
        this.regionJobProfileCache = regionJobProfileCache;
        this.sampleSize = Math.max(1, sampleSize);
        this.topIndustries = Math.max(1, topIndustries);
    }

    public RegionJobProfileView getProfile(SigunguCode region) {
        Optional<RegionJobProfile> cached = regionJobProfileCache.find(region);
        if (cached.isPresent()) {
            return RegionJobProfileView.from(cached.get());
        }

        List<JobPostingSample> samples = regionJobProfileProvider.sample(region, sampleSize);
        RegionJobProfile profile = policy.profile(region, samples, topIndustries);
        if (!profile.isEmpty()) {
            regionJobProfileCache.put(profile);
        }
        return RegionJobProfileView.from(profile);
    }
}
