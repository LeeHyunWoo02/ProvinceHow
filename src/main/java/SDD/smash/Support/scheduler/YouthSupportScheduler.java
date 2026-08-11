package SDD.smash.Support.scheduler;

import SDD.smash.Address.Dto.SigunguCodeDTO;
import SDD.smash.Address.Repository.SigunguRepository;
import SDD.smash.Support.domain.SupportTag;
import SDD.smash.Support.dto.SupportListDTO;
import SDD.smash.Support.service.YouthCenterClient;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

@Component
@RequiredArgsConstructor
public class YouthSupportScheduler {

    private static final Logger log = LoggerFactory.getLogger(YouthSupportScheduler.class);

    private final SigunguRepository sigunguRepository;
    private final YouthCenterClient youthCenterClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, SupportListDTO> listRedisTemplate;

    /**
     * 이후 3일 간격으로 반복 수행
     * initialDelay=0으로 컨텍스트 시작 직후 실행 => 개발시에는 불필요한 트래픽을 줄이기위해 사용x
     * initialDelayString = (day1)으로 시작 후 1일 뒤 스케줄링 시작(개발용)
     */
    @Scheduled(initialDelay = 0,
            fixedDelayString = "#{T(java.time.Duration).ofDays(3).toMillis()}")
    public void runJob()
    {
        long started = System.currentTimeMillis();
        List<SigunguCodeDTO> codes = sigunguRepository.findAllSigunguCodes();
        log.info("[YouthSupportScheduler] 대상 시군구 개수: {}", codes.size());

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();
        ValueOperations<String, SupportListDTO> listOps = listRedisTemplate.opsForValue();

        for(SigunguCodeDTO code : codes)
        {
            for(SupportTag tag : SupportTag.values())
            {
                try{
                    YouthCenterClient.FetchResult result = youthCenterClient.fetch(code.getSigunguCode(), tag);

                    String baseKey = code.getSigunguCode() + ":" + tag.getValue();
                    String numKey = baseKey + ":NUM";

                    ops.set(numKey,result.getTotCount(), Duration.ofDays(10));

                    listOps.set(baseKey,result.getDto(),Duration.ofDays(10));

                    log.info("Cached: {} (totCount={})", baseKey, result.getTotCount());
                }catch(Exception e){
                    log.warn("Fail: code={}, tag={}", code, tag.name());
                }
            }
        }
        log.info("[YouthSupportScheduler] 완료 — elapsed={}ms", (System.currentTimeMillis() - started));

        // 모든 support:score:* 캐시 제거
        redisTemplate.delete(Objects.requireNonNull(redisTemplate.keys("support:score:*")));

    }
}
