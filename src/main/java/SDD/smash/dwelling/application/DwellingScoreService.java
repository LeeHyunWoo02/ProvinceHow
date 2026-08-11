package SDD.smash.dwelling.application;

import SDD.smash.common.domain.model.Money;
import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.application.port.in.DwellingScoreUseCase;
import SDD.smash.dwelling.domain.model.DwellingMarket;
import SDD.smash.dwelling.domain.model.DwellingScoreKey;
import SDD.smash.dwelling.domain.model.DwellingType;
import SDD.smash.dwelling.domain.port.DwellingMarketRepository;
import SDD.smash.dwelling.domain.port.DwellingScoreCache;
import SDD.smash.dwelling.domain.service.DwellingScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 주거 적합도 점수 유스케이스. As-Is {@code DwellingScoreSerivce.getDwellingScoreByType} 의
 * <b>오케스트레이션 부분만</b> 옮긴 것이다(캐시 확인 → 조회 → 정책 적용 → 캐시 저장).
 *
 * <p>가격 보정은 {@link DwellingType#normalize}, 점수 공식은 {@link DwellingScorePolicy},
 * Redis 상세는 {@code DwellingScoreRedisAdapter} 로 각각 빠졌다.
 *
 * <p><b>{@code @Transactional} 을 붙이지 않는다.</b> 이 메서드는 캐시 접근을 포함하는데
 * 트랜잭션 안에서 캐시를 호출하면 커넥션을 쥔 채 네트워크를 기다리게 된다
 * (persistence-conventions §6.3). DB 접근은 {@code findAll()} 한 번뿐이라
 * Spring Data 가 여는 자체 트랜잭션으로 충분하며, 이는 As-Is 와도 같다
 * (As-Is 역시 이 경로에 트랜잭션이 없었다).
 */
@Service
@RequiredArgsConstructor
public class DwellingScoreService implements DwellingScoreUseCase {

    private final DwellingMarketRepository dwellingMarketRepository;
    private final DwellingScoreCache dwellingScoreCache;

    private final DwellingScorePolicy policy = new DwellingScorePolicy();

    @Override
    public Map<SigunguCode, Score> scoresFor(DwellingType type, Money budget) {

        DwellingScoreKey key = DwellingScoreKey.of(type, budget);

        // 1) 캐시 확인
        Optional<Map<SigunguCode, Score>> cached = dwellingScoreCache.find(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) 전 시군구 시세를 읽어 정책을 적용한다.
        //    실거래가 없는 시군구도 결과에 남는다(중앙값 없음 → 0점). As-Is 와 같다.
        Map<SigunguCode, Score> scores = new LinkedHashMap<>();
        for (DwellingMarket market : dwellingMarketRepository.findAll()) {
            scores.put(market.sigunguCode(),
                    policy.score(type, market.medianOf(type).orElse(null), key.normalizedBudget()));
        }

        // 3) 캐시 저장. TTL 은 어댑터가 안다.
        dwellingScoreCache.put(key, scores);

        return scores;
    }
}
