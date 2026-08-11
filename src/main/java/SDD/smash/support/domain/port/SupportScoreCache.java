package SDD.smash.support.domain.port;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.support.domain.model.SupportScoreKey;

import java.util.Map;
import java.util.Optional;

/**
 * 지원정책 점수 캐시 out-port.
 *
 * <p>파생 캐시다. 없으면 다시 계산하면 되므로 캐시 실패가 기능 실패가 되어서는 안 된다.
 */
public interface SupportScoreCache {

    Optional<Map<SigunguCode, Score>> find(SupportScoreKey key);

    void put(SupportScoreKey key, Map<SigunguCode, Score> scores);

    /** 지원정책 원본이 갱신되면 파생 점수를 전부 버린다. */
    void evictAll();
}
