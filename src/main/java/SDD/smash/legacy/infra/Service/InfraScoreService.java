package SDD.smash.legacy.infra.Service;

import SDD.smash.legacy.infra.Entity.Major;
import SDD.smash.legacy.infra.Repository.InfraRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전환 중 임시 공존용 빈 이름 지정.
 * 새 {@code SDD.smash.infra.application.InfraScoreService} 와 기본 빈 이름('infraScoreService')이
 * 겹쳐 컨텍스트가 뜨지 않으므로 옛 쪽에 한정 이름을 준다.
 * 주입은 전부 타입 기준(생성자 주입)이라 이름 변경이 동작에 영향을 주지 않는다.
 * 이 클래스가 사라지는 recommendation 이관 단계에서 함께 제거된다.
 */
@Service("legacyInfraScoreService")
@RequiredArgsConstructor
public class InfraScoreService {
    private static final String REDIS_KEY_PREFIX = "infra:score:";
    private final InfraRepository infraRepository;

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 인프라 중요도에 따라 점수를 보정해 전체 맵 반환
     */
    public Map<String, Integer> getInfraScoresByChoice(Integer infraChoice)
    {
        String redisKey = REDIS_KEY_PREFIX + infraChoice;

        // 캐시 여부 확인
        var hashOps = redisTemplate.opsForHash();
        Map<Object, Object> cached = hashOps.entries(redisKey);
        if (cached != null && !cached.isEmpty()) {
            // 캐시에 있으면 변환해서 반환
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : cached.entrySet()) {
                result.put(String.valueOf(e.getKey()), Integer.valueOf(e.getValue().toString()));
            }
            return result;
        }

        var selectedMajors = Major.fromChoiceMask(infraChoice == null ? 0 : infraChoice);
        //선택항목이 없다면(=0), 빈 맵을 반환하여, 이후 RecommendService에서 자동으로 0으로 계산되도록 함
        if(selectedMajors.isEmpty())
        {
            return Collections.emptyMap();
        }

        // 캐시에 없으면 DB에서 조회
        var rows = infraRepository.sumScoreBySigunguAndMajor(selectedMajors);

        Map<String, Double> sumBySigungu = new HashMap<>();
        for(var r : rows)
        {
            Double toAdd = r.getScore();
            // merge(key, value, accumlator) : key가 없으면 value 삽입, key 있으면 기존값에 더함(시군구는 같고 major는 다른경우)
            sumBySigungu.merge(
                    r.getSigunguCode(),
                    toAdd == null ? 0.0 : toAdd,
                    Double::sum
            );
        }

        int div = selectedMajors.size();
        Map<String, Integer> result = new LinkedHashMap<>();
        for(var e : sumBySigungu.entrySet())
        {
            result.put(e.getKey(), (int) Math.round(e.getValue() / div));
        }

        // 캐싱
        hashOps.putAll(redisKey, new HashMap<>(result));
         redisTemplate.expire(redisKey, Duration.ofHours(24));

        return result;
    }

}
