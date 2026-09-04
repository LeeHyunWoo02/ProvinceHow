package SDD.smash.domain.dwelling.application;

import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.domain.model.DwellingMarket;
import SDD.smash.domain.dwelling.domain.model.DwellingScoreKey;
import SDD.smash.domain.dwelling.domain.model.DwellingType;
import SDD.smash.domain.dwelling.domain.port.DwellingMarketRepository;
import SDD.smash.domain.dwelling.domain.port.DwellingScoreCache;
import SDD.smash.domain.dwelling.domain.service.DwellingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 주거 적합도 점수 유스케이스. As-Is {@code DwellingScoreSerivce.getDwellingScoreByType} 의
 * <b>오케스트레이션 부분만</b> 옮긴 것이다(캐시 확인 → 조회 → 정책 적용 → 캐시 저장).
 *
 * <p>가격 보정은 {@link DwellingType#normalize}, 점수 공식은 {@link DwellingScorePolicy},
 * Redis 상세는 {@code DwellingScoreRedisAdapter} 로 각각 빠졌다.
 *
 * <p><b>{@code scoresFor} 자체에는 {@code @Transactional} 을 붙이지 않는다.</b>
 * 캐시·계산은 트랜잭션 밖에 둔다 — 트랜잭션 안에서 캐시를 호출하면 커넥션을 쥔 채
 * 네트워크를 기다리게 된다(persistence-conventions §6.3). DB 조회부만 {@link #loadMarkets()}
 * 로 잘라 {@code readOnly} 트랜잭션 경계를 두고, 캐시 미스일 때만 호출한다.
 */
@Service
@RequiredArgsConstructor
public class DwellingScoreService {

    private final DwellingMarketRepository dwellingMarketRepository;
    private final DwellingScoreCache dwellingScoreCache;

    private final DwellingScorePolicy policy = new DwellingScorePolicy();

    /** 전 시군구의 주거 적합도. 실거래가 없는 시군구도 0점으로 포함된다. */
    public Map<SigunguCode, Score> scoresFor(DwellingType type, Money budget) {

        DwellingScoreKey key = DwellingScoreKey.of(type, budget);

        // 1) 캐시 확인
        Optional<Map<SigunguCode, Score>> cached = dwellingScoreCache.find(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) 전 시군구 시세를 읽어 정책을 적용한다.
        //    실거래가 없는 시군구도 결과에 남는다(중앙값 없음 → 0점). As-Is 와 같다.
        //    예산 구간화는 키 생성에서 한 번만 하고, 그 값을 정책에 그대로 넘긴다.
        Map<SigunguCode, Score> scores = new LinkedHashMap<>();
        for (DwellingMarket market : loadMarkets()) {
            scores.put(market.sigunguCode(),
                    policy.score(type, market.medianOf(type).orElse(null), key.normalizedBudget()));
        }

        // 3) 캐시 저장. TTL 은 어댑터가 안다.
        dwellingScoreCache.put(key, scores);

        return scores;
    }

    /** 전 시군구 시세 조회부. DB 접근만 트랜잭션 경계로 감싼다(캐시·계산은 밖에 둔다). */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    protected List<DwellingMarket> loadMarkets() {
        return dwellingMarketRepository.findAll();
    }
}
