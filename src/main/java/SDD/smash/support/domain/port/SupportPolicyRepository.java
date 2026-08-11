package SDD.smash.support.domain.port;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.support.domain.model.SupportPolicy;
import SDD.smash.support.domain.model.SupportTag;

import java.util.List;

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

    void saveAll(SigunguCode code, SupportTag tag, List<SupportPolicy> policies);
}
