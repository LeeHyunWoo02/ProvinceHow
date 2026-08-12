package SDD.smash.domain.infra.domain.port;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.domain.model.InfraScoreKey;

import java.util.Map;
import java.util.Optional;

/**
 * 인프라 점수 캐시 out-port.
 *
 * <p>파생 캐시다. 없으면 다시 계산하면 되므로 캐시 실패가 기능 실패가 되어서는 안 된다.
 */
public interface InfraScoreCache {

    Optional<Map<SigunguCode, Score>> find(InfraScoreKey key);

    void put(InfraScoreKey key, Map<SigunguCode, Score> scores);

    /** 인프라 원본이 갱신되면 파생 점수를 전부 버린다. */
    void evictAll();
}
