package SDD.smash.domain.support.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.domain.model.SupportPolicy;
import SDD.smash.domain.support.domain.model.SupportTag;

import java.util.List;
import java.util.Map;

/**
 * 지원정책 정본 저장소 out-port. {@code support} 는 RDB가 없어 이 포트의 구현이
 * {@code infrastructure/cache/SupportPolicyRedisAdapter}(Redis)다.
 *
 * <p>정본 저장소이므로 조회 실패를 캐시처럼 미스로 흡수하지 않는다. 데이터가 없으면
 * 빈 목록/0을 명시적으로 반환하고, 저장소 자체 장애는 예외로 흘려보낸다(redis-conventions §6.3).
 */
public interface SupportPolicyRepository {

    List<SupportPolicy> findBy(SigunguCode code, SupportTag tag);

    int countBy(SigunguCode code, SupportTag tag);

    /**
     * 태그를 고정하고 여러 시군구의 개수를 한 번에 조회한다. 점수 계산이 전 시군구를
     * 순회하며 시군구마다 {@link #countBy} 를 개별 호출하던 것을 태그당 1회로 줄이기 위한 것이다.
     * 데이터가 없는 시군구는 0 으로 채운다(개별 {@link #countBy} 와 동일한 취급).
     */
    Map<SigunguCode, Integer> countByTagForAll(SupportTag tag, List<SigunguCode> codes);

    void saveAll(SigunguCode code, SupportTag tag, List<SupportPolicy> policies);
}
