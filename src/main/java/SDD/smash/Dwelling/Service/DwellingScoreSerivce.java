package SDD.smash.Dwelling.Service;

import SDD.smash.Dwelling.Dto.DwellingJeonseDTO;
import SDD.smash.Dwelling.Dto.DwellingMonthDTO;
import SDD.smash.Dwelling.Entity.DwellingType;
import SDD.smash.Dwelling.Repository.DwellingRepository;
import SDD.smash.common.exception.ErrorCode;
import SDD.smash.common.exception.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DwellingScoreSerivce {
    private static final String REDIS_KEY_PREFIX = "dwelling:score";

    private final RedisTemplate<String, Object> redisTemplate;
    private final DwellingRepository dwellingRepository;

    public Map<String, Integer> getDwellingScoreByType(DwellingType type, Integer price)
    {
        price = validPrice(type, price);

        String redisKey = REDIS_KEY_PREFIX + ":" + type.name() + ":" + price;

        var hashOps = redisTemplate.opsForHash();

        //캐시 확인
        Map<Object, Object> cached = hashOps.entries(redisKey);
        if(cached != null && !cached.isEmpty())
        {
            //이미 존재하면 캐시 리턴
            Map<String, Integer> result = new LinkedHashMap<>();
            for(Map.Entry<Object, Object> e : cached.entrySet())
            {
                result.put((String) e.getKey(), (Integer) e.getValue());
            }
            return result;
        }

        //미스
        Map<String, Integer> dwellingScoreMap = new LinkedHashMap<>();

        if(type == DwellingType.MONTHLY)
        {
            //월세 중앙 값 리스트를 가져온 뒤, 해당 시군구의 monthMid와 10단위 차이마다 100점에서 10점씩 감점(ex. monthMid=80이고 price=60인 경우 20차이므로 100-20 = 80), 최소 0점
            List<DwellingMonthDTO> dwellingScores = dwellingRepository.getAllDwellingMonth();
            int score;
            for(DwellingMonthDTO monthDTO : dwellingScores)
            {
                Integer monthMid = monthDTO.getMonthMid();
                score = calcMonthlyScore(monthMid, price);
                dwellingScoreMap.put(monthDTO.getSigunguCode(), score);
            }
        }
        else
        {
            //전세 중앙 값 리스트를 가져온 뒤, 해당 시군구의 jeonseMid와 3000단위 차이마다 100점에서 10점씩 감점(ex. jeonseMid=8000이고 price=10000인 경우 2000차이므로 100-20 = 80), 최소 0점
            List<DwellingJeonseDTO> dwellingScores = dwellingRepository.getAllDwellingJeonse();
            int score;
            for(DwellingJeonseDTO jeonseDTO : dwellingScores)
            {
                Integer jeonseMid = jeonseDTO.getJeonseMid();
                score = calcJeonseScore(jeonseMid, price);
                dwellingScoreMap.put(jeonseDTO.getSigunguCode(), score);
            }
        }

        // 캐싱
        hashOps.putAll(redisKey, new LinkedHashMap<>(dwellingScoreMap));
        redisTemplate.expire(redisKey, Duration.ofDays(30));

        return dwellingScoreMap;
    }


    /**
     * 월세의 경우 20만원~110만원이상의 범위로 변환
     * 전세의 경우 3000만원~21000만원이상 범위로 변경한다.
     * 110, 21000의 경우 이상의 의미를 가짐.
     */
    private Integer validPrice(DwellingType type, Integer price)
    {
        if (price == null) {
            throw new DomainException(ErrorCode.PRICE_AMOUNT_NOT_VALID, "가격이 입력되지 않았습니다.");
        }
        if(type == DwellingType.MONTHLY)
        {
            int adjusted = (int) (Math.round(price / 10.0) * 10);
            return Math.max(20, Math.min(adjusted, 110));

        }
        else // 3 6 9 12 15 18 21
        {
            int adjusted = (int) (Math.round(price / 3000.0)*3000);
            return Math.max(3000, Math.min(adjusted,21000));
        }
    }

    /**
     * 월세 점수 계산
     * - diff = |중앙값 - 사용자가격|
     * - 10단위 차이마다 10점 감점
     * - 최소 0점
     */
    int calcMonthlyScore(Integer monthMid, Integer price)
    {
        if(monthMid == null) return 0;
        if(price == 110 && monthMid >= price) return 100;

        int diff = Math.abs(monthMid-price);
        int penalty = (diff / 10) * 10;
        int score = 100 - penalty;
        return Math.max(score, 0);
    }

    /**
     * 전세 점수 계산
     * - diff = |중앙값 - 사용자가격|
     * - 3000단위 차이마다 10점 감점
     * - 최소 0점
     */
    int calcJeonseScore(Integer jeonseMid, Integer price)
    {
        if(jeonseMid == null) return 0;
        if(price == 21000 && jeonseMid >= price) return 100;

        int diff = Math.abs(jeonseMid-price);
        int penalty = (diff / 3000) * 10;
        int score = 100 - penalty;
        return Math.max(score, 0);
    }

}
