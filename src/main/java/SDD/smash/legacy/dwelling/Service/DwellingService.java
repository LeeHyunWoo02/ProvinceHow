package SDD.smash.legacy.dwelling.Service;

import SDD.smash.legacy.address.Service.AddressVerifyService;
import SDD.smash.legacy.dwelling.Dto.DwellingInfoDTO;
import SDD.smash.legacy.dwelling.Dto.DwellingSimpleInfoDTO;
import SDD.smash.legacy.dwelling.Repository.DwellingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class DwellingService {

    private final AddressVerifyService addressVerifyService;
    private final DwellingRepository dwellingRepository;

    /**
     * 해당 시군구의 월세 및 전세 중앙값 반환(추천조회용)
     * 데이터가 없는 경우(시군구에 아파트가 없거나, 최근 실거래 내역이 없는 경우 null로 반환됨)
     */
    public DwellingSimpleInfoDTO getDwellingSimpleInfo(String sigungu)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigungu);

        Optional<DwellingSimpleInfoDTO> _dto = dwellingRepository.getDwellingSimpleInfo(sigungu);
        return _dto.orElse(null);
    }

    /**
     * 해당 시군구의 월세 및 전세 평균&중앙값 반환
     * 데이터가 없는 경우(시군구에 아파트가 없거나, 최근 실거래 내역이 없는 경우 null로 반환됨)
     */
    public DwellingInfoDTO getDwellingInfo(String sigungu)
    {
        addressVerifyService.checkSigunguCodeOrThrow(sigungu);

        Optional<DwellingInfoDTO> _dto = dwellingRepository.getDwellingInfo(sigungu);
        return _dto.orElse(null);
    }
}
