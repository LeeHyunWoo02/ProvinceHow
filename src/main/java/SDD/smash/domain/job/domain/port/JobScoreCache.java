package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobScoreKey;

import java.util.Map;
import java.util.Optional;

/**
 * 일자리 점수 캐시 out-port.
 *
 * <p>파생 캐시다. 없으면 다시 계산하면 되므로 캐시 실패가 기능 실패가 되어서는 안 된다.
 */
public interface JobScoreCache {

    Optional<Map<SigunguCode, Score>> find(JobScoreKey key);

    void put(JobScoreKey key, Map<SigunguCode, Score> scores);

    /** 일자리 수 원본이 갱신되면 파생 점수를 전부 버린다. */
    void evictAll();
}
