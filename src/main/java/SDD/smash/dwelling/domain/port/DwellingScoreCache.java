package SDD.smash.dwelling.domain.port;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.domain.model.DwellingScoreKey;

import java.util.Map;
import java.util.Optional;

/**
 * 주거 점수 캐시 out-port.
 *
 * <p>파생 캐시다. 없으면 다시 계산하면 되므로 캐시 실패가 기능 실패가 되어서는 안 된다.
 * 저장 매체가 Redis 인지 인메모리인지는 이 인터페이스 뒤에 숨는다.
 */
public interface DwellingScoreCache {

    Optional<Map<SigunguCode, Score>> find(DwellingScoreKey key);

    void put(DwellingScoreKey key, Map<SigunguCode, Score> scores);

    /** 원본 시세가 갱신되면 파생 점수를 전부 버린다. */
    void evictAll();
}
