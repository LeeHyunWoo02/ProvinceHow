package SDD.smash.domain.infra.application;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.infra.application.port.in.InfraScoreUseCase;
import SDD.smash.domain.infra.domain.model.InfraScoreKey;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.model.RegionMajorScore;
import SDD.smash.domain.infra.domain.port.InfraScoreCache;
import SDD.smash.domain.infra.domain.port.RegionMajorScoreRepository;
import SDD.smash.domain.infra.domain.service.InfraScorePolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 인프라 적합도 점수 유스케이스. As-Is {@code InfraScoreService.getInfraScoresByChoice} 의
 * <b>오케스트레이션 부분만</b> 옮긴 것이다.
 *
 * <p><b>순서를 그대로 유지한다</b> — As-Is 는 캐시 확인이 선택 항목 판정보다 먼저다.
 * <ol>
 *   <li>캐시 확인 (미스일 때만 아래로 진행)</li>
 *   <li>{@code infraChoice} 를 대분류 집합으로 해석</li>
 *   <li>선택한 대분류가 없으면 빈 맵 반환(캐시하지 않음)</li>
 *   <li>원천 데이터 조회 → 정책 적용 → 캐시 저장</li>
 * </ol>
 *
 * <p>{@code @Transactional} 을 붙이지 않는다. 캐시 접근을 포함하는 메서드라
 * 트랜잭션으로 감싸면 커넥션을 쥔 채 네트워크를 기다리게 된다(persistence-conventions §6.3).
 * As-Is 도 이 경로에 트랜잭션이 없었다.
 */
@Service
@RequiredArgsConstructor
public class InfraScoreService implements InfraScoreUseCase {

    private final RegionMajorScoreRepository regionMajorScoreRepository;
    private final InfraScoreCache infraScoreCache;

    private final InfraScorePolicy policy = new InfraScorePolicy();

    @Override
    public Map<SigunguCode, Score> scoresFor(Integer infraChoice) {

        InfraScoreKey key = InfraScoreKey.of(infraChoice);

        // 1) 캐시 확인. 선택 항목이 없어도 일단 캐시부터 본다(As-Is 순서).
        Optional<Map<SigunguCode, Score>> cached = infraScoreCache.find(key);
        if (cached.isPresent()) {
            return cached.get();
        }

        // 2) 선택 항목이 없으면 빈 맵. 이 경로는 캐시하지 않는다.
        EnumSet<Major> selectedMajors = Major.fromChoiceMask(infraChoice == null ? 0 : infraChoice);
        if (selectedMajors.isEmpty()) {
            return Map.of();
        }

        // 3) 원천 데이터 조회 + 정책 적용
        List<RegionMajorScore> regionScores = regionMajorScoreRepository.findAllBy(selectedMajors);
        Map<SigunguCode, Score> scores = policy.scores(selectedMajors, regionScores);

        // 4) 캐시 저장. TTL 은 어댑터가 안다.
        infraScoreCache.put(key, scores);

        return scores;
    }
}
