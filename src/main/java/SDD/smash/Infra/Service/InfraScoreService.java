package SDD.smash.Infra.Service;

<<<<<<< HEAD
import SDD.smash.Infra.Entity.Major;
import SDD.smash.Infra.Repository.InfraRepository;
=======
import SDD.smash.Infra.Entity.InfraImportance;
import SDD.smash.Infra.Entity.InfraScore;
import SDD.smash.Infra.Repository.InfraScoreRepository;
>>>>>>> origin/Backup/main
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
<<<<<<< HEAD
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
=======
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
>>>>>>> origin/Backup/main
import java.util.Map;

@Service
@RequiredArgsConstructor
public class InfraScoreService {
    private static final String REDIS_KEY_PREFIX = "infra:score:";
<<<<<<< HEAD
    private final InfraRepository infraRepository;
=======
    private final InfraScoreRepository infraScoreRepository;
>>>>>>> origin/Backup/main

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 인프라 중요도에 따라 점수를 보정해 전체 맵 반환
     */
<<<<<<< HEAD
    public Map<String, Integer> getInfraScoresByChoice(Integer infraChoice)
    {
        String redisKey = REDIS_KEY_PREFIX + infraChoice;
=======
    public Map<String, Integer> getInfraScoresByImportance(InfraImportance infraImportance)
    {
        String redisKey = REDIS_KEY_PREFIX + infraImportance.name();
>>>>>>> origin/Backup/main

        // 캐시 여부 확인
        var hashOps = redisTemplate.opsForHash();
        Map<Object, Object> cached = hashOps.entries(redisKey);
        if (cached != null && !cached.isEmpty()) {
            // 캐시에 있으면 변환해서 반환
            Map<String, Integer> result = new LinkedHashMap<>();
            for (Map.Entry<Object, Object> e : cached.entrySet()) {
<<<<<<< HEAD
                result.put(String.valueOf(e.getKey()), Integer.valueOf(e.getValue().toString()));
=======
                result.put((String) e.getKey(), (Integer) e.getValue());
>>>>>>> origin/Backup/main
            }
            return result;
        }

<<<<<<< HEAD
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

=======
        // 캐시에 없으면 DB에서 조회
        List<InfraScore> scores = infraScoreRepository.findAllByOrderByScoreDesc();

        // 중요도에 따라 가공
        Map<String, Integer> processed = switch (infraImportance) {
            case MID -> applyMidRule(scores);
            case LOW -> applyLowRule(scores);
            case HIGH -> applyHighRule(scores); // 원본 그대로
        };

        // 캐싱
        hashOps.putAll(redisKey, new HashMap<>(processed));
         redisTemplate.expire(redisKey, Duration.ofHours(24));

        return processed;
    }


    /**
     * MID 규칙:
     * - 전체는 원래 점수 유지
     * - 단, "상위 21~200" 번째만 +30
     */
    private Map<String, Integer> applyMidRule(List<InfraScore> scores) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < scores.size(); i++) {
            InfraScore is = scores.get(i);
            int original = defaultScore(is.getScore());
            int finalScore = original;
            // index 0-based 이므로 21~200 → 20~199
            if (i >= 20 && i <= 199) {
                finalScore = original + 30;
            }
            result.put(is.getSigunguCode(), finalScore);
        }
        return result;
    }

    /**
     * LOW 규칙:
     * - 모두 0으로
     */
    private Map<String, Integer> applyLowRule(List<InfraScore> scores) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (InfraScore is : scores) {
            result.put(is.getSigunguCode(), 0);
        }
        return result;
    }

    /**
     * HIGH 규칙:
     * - 원본 그대로
     */
    private Map<String, Integer> applyHighRule(List<InfraScore> scores) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (InfraScore is : scores) {
            result.put(is.getSigunguCode(), defaultScore(is.getScore()));
        }
        return result;
    }

    private int defaultScore(Integer score) {
        return score != null ? score : 0;
    }

>>>>>>> origin/Backup/main
}
