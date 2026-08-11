package SDD.smash.dwelling.application;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.application.dto.DwellingInfo;
import SDD.smash.dwelling.application.dto.DwellingSimpleInfo;
import SDD.smash.dwelling.application.port.in.DwellingQueryUseCase;
import SDD.smash.dwelling.domain.port.DwellingMarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전월세 시세 조회 유스케이스. As-Is {@code DwellingService} 를 옮긴 것이다.
 *
 * <p>시군구 존재 검증은 address 컨텍스트의 in-port 에 위임한다.
 * As-Is 가 {@code AddressVerifyService.checkSigunguCodeOrThrow} 를 부르던 자리이며
 * 던지는 {@code ErrorCode} 도 {@code ADDRESS_CODE_NOT_FOUND} 로 같다.
 */
@Service
@RequiredArgsConstructor
public class DwellingQueryService implements DwellingQueryUseCase {

    private final AddressQueryUseCase addressQueryUseCase;
    private final DwellingMarketRepository dwellingMarketRepository;

    /**
     * 해당 시군구의 월세·전세 중앙값. 실거래가 없으면 {@code null} 이다
     * (시군구에 아파트가 없거나 최근 실거래가 없는 경우 — As-Is 와 동일).
     */
    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public DwellingSimpleInfo getDwellingSimpleInfo(SigunguCode code) {
        addressQueryUseCase.checkSigunguExistsOrThrow(code);

        return dwellingMarketRepository.findBy(code)
                .map(DwellingSimpleInfo::from)
                .orElse(null);
    }

    /** 해당 시군구의 월세·전세 평균과 중앙값. 실거래가 없으면 {@code null} 이다. */
    @Override
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public DwellingInfo getDwellingInfo(SigunguCode code) {
        addressQueryUseCase.checkSigunguExistsOrThrow(code);

        return dwellingMarketRepository.findBy(code)
                .map(DwellingInfo::from)
                .orElse(null);
    }
}
