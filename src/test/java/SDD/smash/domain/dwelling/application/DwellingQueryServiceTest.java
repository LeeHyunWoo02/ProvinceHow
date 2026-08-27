package SDD.smash.domain.dwelling.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.dwelling.application.dto.DwellingTypeInfo;
import SDD.smash.domain.dwelling.domain.model.DwellingTypeStat;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.RentStat;
import SDD.smash.domain.dwelling.domain.port.DwellingMarketRepository;
import SDD.smash.domain.dwelling.domain.port.DwellingTypeStatRepository;
import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

/** 주택유형별 시세 조회 유스케이스. 포트만 목킹한다. */
@ExtendWith(MockitoExtension.class)
class DwellingQueryServiceTest {

    private static final SigunguCode CODE = SigunguCode.of("11680");

    @Mock
    AddressQueryService addressQueryService;

    @Mock
    DwellingMarketRepository dwellingMarketRepository;

    @Mock
    DwellingTypeStatRepository dwellingTypeStatRepository;

    @InjectMocks
    DwellingQueryService dwellingQueryService;

    @Test
    @DisplayName("유형별 시세를 HousingType 선언 순서로 정렬해 반환한다")
    void returnsDwellingByTypeSortedByHousingTypeDeclarationOrder() {
        // given - 저장소가 선언 순서와 다르게 돌려준다
        given(dwellingTypeStatRepository.findAllBy(CODE)).willReturn(List.of(
                stat(HousingType.DETACHED_HOUSE, 30.0, 25, 9000.0, 8500),
                stat(HousingType.APARTMENT, 70.5, 65, 25000.0, 24000),
                stat(HousingType.MULTIPLEX_HOUSE, 45.0, 40, 15000.0, 14000)));

        // when
        List<DwellingTypeInfo> result = dwellingQueryService.getDwellingByType(CODE);

        // then
        assertThat(result).extracting(DwellingTypeInfo::housingType)
                .containsExactly(HousingType.APARTMENT, HousingType.MULTIPLEX_HOUSE, HousingType.DETACHED_HOUSE);
        assertThat(result.get(0).monthAvg()).isEqualTo(70.5);
        assertThat(result.get(0).monthMid()).isEqualTo(Money.of(65));
        assertThat(result.get(0).jeonseAvg()).isEqualTo(25000.0);
        assertThat(result.get(0).jeonseMid()).isEqualTo(Money.of(24000));
    }

    @Test
    @DisplayName("시군구가 없으면 ADDRESS_CODE_NOT_FOUND 를 던지고 저장소를 조회하지 않는다")
    void throwsWhenSigunguDoesNotExist() {
        // given
        willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"))
                .given(addressQueryService).checkSigunguExistsOrThrow(any());

        // when & then
        assertThatThrownBy(() -> dwellingQueryService.getDwellingByType(CODE))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
        then(dwellingTypeStatRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("일부 유형만 적재돼 있으면 그 유형만 반환하고 통계가 없는 값은 null 이다")
    void returnsOnlyStoredHousingTypes() {
        // given - 아파트만 있고 전세 실거래가 없다
        given(dwellingTypeStatRepository.findAllBy(CODE)).willReturn(List.of(
                new DwellingTypeStat(HousingType.APARTMENT, RentStat.of(70.5, 65), RentStat.EMPTY)));

        // when
        List<DwellingTypeInfo> result = dwellingQueryService.getDwellingByType(CODE);

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).housingType()).isEqualTo(HousingType.APARTMENT);
        assertThat(result.get(0).monthMid()).isEqualTo(Money.of(65));
        assertThat(result.get(0).jeonseAvg()).isNull();
        assertThat(result.get(0).jeonseMid()).isNull();
    }

    @Test
    @DisplayName("적재된 유형이 없으면 null 이 아니라 빈 리스트를 반환한다")
    void returnsEmptyListWhenNoHousingTypeStored() {
        // given
        given(dwellingTypeStatRepository.findAllBy(CODE)).willReturn(List.of());

        // when
        List<DwellingTypeInfo> result = dwellingQueryService.getDwellingByType(CODE);

        // then
        assertThat(result).isNotNull().isEmpty();
    }

    private DwellingTypeStat stat(HousingType type, Double monthAvg, Integer monthMid,
                                  Double jeonseAvg, Integer jeonseMid) {
        return new DwellingTypeStat(type, RentStat.of(monthAvg, monthMid), RentStat.of(jeonseAvg, jeonseMid));
    }
}
