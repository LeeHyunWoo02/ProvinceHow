package SDD.smash.Support.service;

import SDD.smash.Address.Service.AddressVerifyService;
import SDD.smash.Support.domain.SupportTag;
import SDD.smash.Support.dto.SupportDTO;
import SDD.smash.Support.dto.SupportListDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SupportService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisTemplate<String, SupportListDTO> listRedisTemplate;

    private final AddressVerifyService addressVerifyService;

    /**
     * 해당 시군구의 모든 정책 개수 반환
     * 정책정보가 없는 경우 null
     */
    public Integer getAllSupportNum(String sigunguCode)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        Integer total = null;

        for(SupportTag tag : SupportTag.values())
        {
            String numKey = sigunguCode + ":" + tag.getValue() + ":" + "NUM";
            Object value = ops.get(numKey);
            if(value instanceof Number)
            {
                if(total == null) total = 0;
                total += ((Number)value).intValue();
            }
        }
        return total;
    }

    /**
     * 해당 시군구의 특정 태그의 정책 개수 반환
     * 정책정보가 없는 경우 null
     */
    public Integer getFitSupportNum(String sigunguCode, Integer supportChoice)
    {
        if(supportChoice == null || supportChoice == 0) return null;

        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        var selectedTags = SupportTag.fromChoiceMask(supportChoice);
        ValueOperations<String, Object> ops = redisTemplate.opsForValue();

        int sum = 0;
        for(SupportTag tag : selectedTags)
        {
            String numKey = sigunguCode + ":" + tag.getValue() + ":" + "NUM";
            Object value = ops.get(numKey);
            if(value instanceof Number)
            {
                sum += ((Number)value).intValue();
            }
        }
        return sum;
    }

    /**
     * 해당 시군구의 모든 정책 세부 정보 리스트 반환
     * 데이터가 없는 경우 null (!= 0)
     */
    public SupportListDTO getAllSupportList(String sigunguCode)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        ValueOperations<String, SupportListDTO> ops = listRedisTemplate.opsForValue();

        SupportListDTO dto = null;

        for(SupportTag tag : SupportTag.values())
        {
            String baseKey = sigunguCode + ":" + tag.getValue();
            SupportListDTO value = ops.get(baseKey);
            if(value != null)
            {
                if(dto == null)
                {
                    dto = new SupportListDTO();
                    dto.setSupportDTOList(new ArrayList<SupportDTO>());
                }
                List<SupportDTO> list = value.getSupportDTOList();
                dto.getSupportDTOList().addAll(list);
            }
        }
        return dto;
    }
}
