package SDD.smash.domain.job.domain.port;

import SDD.smash.domain.job.domain.model.NonCapitalRatioSnapshot;
import SDD.smash.domain.job.domain.model.StatisticsMonth;

import java.util.Optional;

/**
 * 비수도권 구인배수 분포 캐시 out-port.
 *
 * <p>백분위는 모집단 전체가 있어야 계산되는데, 그 모집단은 최신월 전국 통계다.
 * 요청마다 다시 읽지 않도록 접어 둔 결과를 보관한다. 파생 캐시이므로 없으면 다시 만들면 된다.
 */
public interface NonCapitalRatioCache {

    /** 해당 기준월의 분포. 다른 월이 담겨 있거나 비어 있으면 미스다. */
    Optional<NonCapitalRatioSnapshot> find(StatisticsMonth month);

    void put(NonCapitalRatioSnapshot snapshot);

    void evictAll();
}
