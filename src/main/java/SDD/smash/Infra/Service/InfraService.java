package SDD.smash.Infra.Service;

import SDD.smash.Address.Service.AddressVerifyService;
import SDD.smash.Infra.Dto.InfraDetails;
import SDD.smash.Infra.Dto.InfraMajor;
import SDD.smash.Infra.Entity.Major;
import SDD.smash.Infra.Repository.InfraRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InfraService {

    private final InfraRepository infraRepository;
    private final AddressVerifyService addressVerifyService;

    /**
     * 해당 시군구의 주 인프라(4종) 개수 및 점수 반환(지역추천 반환용)
     */
    public List<InfraMajor> getMajorInfraNumAndScore(String sigunguCode)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);
        List<InfraMajor> list = new ArrayList<>();

        for(Major major : Major.values())
        {
            Optional<InfraMajor> _infraMajor = infraRepository.getInfraMajor(sigunguCode, major);
            if(_infraMajor.isPresent())
            {
                list.add(_infraMajor.get());
                continue;
            }
            //인프라 정보가 없는 경우 -> 적재된 데이터의 문제라서 로그찍고 넘어감
            log.warn("{}지역의 인프라 정보가 없습니다.", sigunguCode);
        }
        return list;
    }

    /**
     * 해당 시군구의 상세 인프라(14종) 개수 반환(지역상세 반환용)
     * @param sigunguCode
     * @return
     */
    public List<InfraDetails> getInfraDetails(String sigunguCode)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        return infraRepository.getInfraDetails(sigunguCode);
    }
}
