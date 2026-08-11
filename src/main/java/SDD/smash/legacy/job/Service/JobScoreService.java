package SDD.smash.legacy.job.Service;

import SDD.smash.common.exception.ErrorCode;
import SDD.smash.common.exception.DomainException;
import SDD.smash.legacy.job.Dto.JobCountDTO;
import SDD.smash.legacy.job.Repository.JobCodeMiddleRepository;
import SDD.smash.legacy.job.Repository.JobCountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 전환 중 임시 공존용 빈 이름 지정.
 * 새 {@code SDD.smash.job.application.JobScoreService} 와 기본 빈 이름('jobScoreService')이 겹쳐
 * 컨텍스트가 뜨지 않으므로 옛 쪽에 한정 이름을 준다.
 * 주입은 전부 타입 기준(생성자 주입)이라 이름 변경이 동작에 영향을 주지 않는다.
 * 이 클래스가 사라지는 recommendation 이관 단계에서 함께 제거된다.
 */
@Service("legacyJobScoreService")
@RequiredArgsConstructor
public class JobScoreService {

    private static final String REDIS_KEY_PREFIX = "job:score:";

    private final JobCountRepository jobCountRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private final JobCodeMiddleRepository jobCodeMiddleRepository;


    /**
     * jobCodeMiddleCode
     *  - null  → 시군구별 전체 일자리 수 기준
     *  - not null → 해당 중분류 일자리 수 기준
     */
    public Map<String, Integer> getJobScore(String jobCodeMiddleCode)
    {



        //jobcode 존재 확인
        if(jobCodeMiddleCode != null && !jobCodeMiddleRepository.existsByCode(jobCodeMiddleCode))
        {
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다.");
        }

        String jobCodeKey = (jobCodeMiddleCode != null) ? jobCodeMiddleCode : "default";
        String baseKey = REDIS_KEY_PREFIX + jobCodeKey;

        var hashOps = redisTemplate.opsForHash();

        // 캐시 확인
        Map<Object, Object> cached = hashOps.entries(baseKey);
        if(cached != null && !cached.isEmpty())
        {
            Map<String, Integer> result = new LinkedHashMap<>();
            for(Map.Entry<Object, Object> e : cached.entrySet())
            {
                result.put((String) e.getKey(), (Integer) e.getValue());
            }
            return result;
        }

        //미스
        List<JobCountDTO> list;

        //case 1: jobMidCode가 없는 경우 -> sigungu별 전체 일자리 개수로 백분율을 매겨 0~100사이의 점수로 만든다.
        if(jobCodeMiddleCode == null)
        {
            list = jobCountRepository.findAllTotalJobCount();
        }
        else //case 2: jobMidCode가 있는 경우 -> sigungu별 해당 jobCodeMiddle.code의 count를 기준으로 백분율을 매겨 0~100 사이의 점수로 만든다.
        {
            list = jobCountRepository.findAllJobCode(jobCodeMiddleCode);
        }

        //최대값 구하기
        long maxCount = 0L;
        for(JobCountDTO dto : list)
        {
            Long c = dto.getTotalCount();
            if(c != null && c > maxCount)
                maxCount = c;
        }

        //점수화
        Map<String, Integer> scoreMap = new LinkedHashMap<>();

        if (maxCount == 0) {
            // 데이터는 있는데 전부 0인 경우 → 전부 0점
            for (JobCountDTO dto : list)
            {
                scoreMap.put(dto.getSigunguCode(), 0);
            }
        } else {
            for (JobCountDTO dto : list)
            {
                long count = dto.getTotalCount() == null ? 0L : dto.getTotalCount();
                // 비율 → 0~100
                int score = (int) Math.floor((count * 100.0) / maxCount);
                scoreMap.put(dto.getSigunguCode(), score);
            }
        }

        //캐싱
        hashOps.putAll(baseKey, new HashMap<>(scoreMap));
        redisTemplate.expire(baseKey, Duration.ofHours(12));

        return scoreMap;
    }
}
