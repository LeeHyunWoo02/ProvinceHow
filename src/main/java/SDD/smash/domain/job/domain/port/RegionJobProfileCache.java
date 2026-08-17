package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.RegionJobProfile;

import java.util.Optional;

/**
 * 지역 채용 프로필 캐시 out-port. 파생 캐시다.
 *
 * <p>사람인 1일 500회 제한 때문에 계산된 프로필을 지역별로 캐싱한다. 없으면 다시 표본을 받아
 * 재계산하면 되므로 캐시 실패가 기능 실패가 되어선 안 된다. 원본이 외부 공급자(사람인)라
 * 무효화 시점이 없어 TTL 로만 신선도를 유지한다. {@link #evictAll()} 은 포트 규약용이며
 * 현재 호출부는 없다.
 */
public interface RegionJobProfileCache {

    Optional<RegionJobProfile> find(SigunguCode region);

    void put(RegionJobProfile profile);

    void evictAll();
}
