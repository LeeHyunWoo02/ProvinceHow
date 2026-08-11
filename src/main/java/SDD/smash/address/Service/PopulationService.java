package SDD.smash.Address.Service;

import SDD.smash.Address.Repository.PopulationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PopulationService {

    private final PopulationRepository populationRepository;
    private final AddressVerifyService addressVerifyService;

    /**
     * 해당 시군구의 인구수 반환
     * 만약 해당 시군구 정보가 없다면 null 반환
     */
    public Integer getPopulationBySigunguCode(String sigunguCode)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        Optional<Integer> _population = populationRepository.findPopulationCountBySigunguCode(sigunguCode);

        return _population.orElse(null);
    }
}
