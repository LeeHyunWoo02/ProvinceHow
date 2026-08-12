package SDD.smash.domain.support.application;

import SDD.smash.domain.address.application.port.in.AddressQueryUseCase;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.application.port.in.SupportScoreUseCase;
import SDD.smash.domain.support.domain.model.SupportScoreKey;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import SDD.smash.domain.support.domain.port.SupportScoreCache;
import SDD.smash.domain.support.domain.service.SupportScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 지원정책 적합도 점수 유스케이스. As-Is {@code SupportScoreService.getSupportScoresByTag} 의
 * <b>오케스트레이션 부분만</b> 옮긴 것이다.
 *
 * <p>순서를 그대로 유지한다 — 캐시 확인이 선택 태그 판정보다 먼저다(infra 와 같은 순서).
 * 시군구 목록은 옛 {@code SigunguRepository} 대신 address 의 in-port 에서 받는다.
 *
 * <p>{@code @Transactional} 을 붙이지 않는다. RDB 가 없는 컨텍스트이고, 캐시 접근을
 * 포함하는 메서드다.
 */
@Service
@RequiredArgsConstructor
public class SupportScoreService implements SupportScoreUseCase {

    private final AddressQueryUseCase addressQueryUseCase;
    private final SupportPolicyRepository supportPolicyRepository;
    private final SupportScoreCache supportScoreCache;

    private final SupportScorePolicy policy = new SupportScorePolicy();

    @Override
    public Map<SigunguCode, Score> scoresFor(Integer supportChoice) {

        SupportScoreKey key = SupportScoreKey.of(supportChoice);

        // 1) 캐시 확인
        Optional<Map<SigunguCode, Score>> cached = supportScoreCache.find(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) 선택 항목이 없으면 빈 맵. 이 경로는 캐시하지 않는다.
        EnumSet<SupportTag> selectedTags = SupportTag.fromChoiceMask(supportChoice == null ? 0 : supportChoice);
        if (selectedTags.isEmpty()) {
            return Map.of();
        }

        // 3) 시군구별로 선택한 태그들의 개수를 모아 정책을 적용한다.
        Map<SigunguCode, Score> scores = new LinkedHashMap<>();
        for (SigunguCode sigunguCode : addressQueryUseCase.getAllSigunguCodes()) {
            Map<SupportTag, Integer> countsByTag = new LinkedHashMap<>();
            for (SupportTag tag : selectedTags) {
                countsByTag.put(tag, supportPolicyRepository.countBy(sigunguCode, tag));
            }
            scores.put(sigunguCode, policy.score(countsByTag, selectedTags));
        }

        // 4) 캐시 저장. TTL 은 어댑터가 안다.
        supportScoreCache.put(key, scores);

        return scores;
    }
}
