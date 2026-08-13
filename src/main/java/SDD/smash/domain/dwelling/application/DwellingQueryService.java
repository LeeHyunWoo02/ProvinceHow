package SDD.smash.domain.dwelling.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.application.dto.DwellingInfo;
import SDD.smash.domain.dwelling.application.dto.DwellingSimpleInfo;
import SDD.smash.domain.dwelling.domain.port.DwellingMarketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전월세 시세 조회 유스케이스. {@code recommendation} 이 dwelling 을 호출하는 통로다.
 *
 * <p>시군구 존재 검증은 address 컨텍스트의 application Service 에 위임한다.
 * 던지는 {@code ErrorCode} 는 {@code ADDRESS_CODE_NOT_FOUND} 다.
 */
@Service
@RequiredArgsConstructor
public class DwellingQueryService {

    private final AddressQueryService addressQueryService;
    private final DwellingMarketRepository dwellingMarketRepository;

    /**
     * 해당 시군구의 월세·전세 중앙값. 실거래가 없으면 {@code null} 이다
     * (시군구에 아파트가 없거나 최근 실거래가 없는 경우 — As-Is 와 동일).
     */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public DwellingSimpleInfo getDwellingSimpleInfo(SigunguCode code) {
        addressQueryService.checkSigunguExistsOrThrow(code);

        return dwellingMarketRepository.findBy(code)
                .map(DwellingSimpleInfo::from)
                .orElse(null);
    }

    /** 해당 시군구의 월세·전세 평균과 중앙값. 실거래가 없으면 {@code null} 이다. */
    @Transactional(transactionManager = "dataTransactionManager", readOnly = true)
    public DwellingInfo getDwellingInfo(SigunguCode code) {
        addressQueryService.checkSigunguExistsOrThrow(code);

        return dwellingMarketRepository.findBy(code)
                .map(DwellingInfo::from)
                .orElse(null);
    }
}
