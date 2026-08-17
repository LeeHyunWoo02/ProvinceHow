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
 *
 * <p><b>네거티브 캐싱</b>: 표본 0건(빈 프로필)도 {@code put} 하면 짧은 TTL 로 캐싱한다(재호출 방지).
 * {@code find} 는 키가 있으면 그 프로필(빈 프로필 포함)을 돌려주고, 키가 없을 때만
 * {@link Optional#empty()}(미스)다.
 */
public interface RegionJobProfileCache {

    /** @return 키가 있으면 캐시된 프로필(빈 프로필 포함). 키가 없으면 {@link Optional#empty()}. */
    Optional<RegionJobProfile> find(SigunguCode region);

    /** 빈 프로필(표본 0건)을 주면 짧은 TTL 로 네거티브 캐싱, 아니면 정상 TTL 로 캐싱한다. */
    void put(RegionJobProfile profile);

    void evictAll();
}
