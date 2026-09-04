package SDD.smash.domain.address.application;

import SDD.smash.domain.address.domain.model.Population;
import SDD.smash.domain.address.domain.port.PopulationRepository;
import SDD.smash.domain.address.domain.port.SigunguRepository;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class PopulationQueryServiceTest {

    @Mock
    SigunguRepository sigunguRepository;

    @Mock
    PopulationRepository populationRepository;

    @InjectMocks
    PopulationQueryService populationQueryService;

    @Test
    @DisplayName("시군구와 인구가 모두 있으면 인구수를 돌려준다")
    void returnsPopulationCountWhenPresent() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("11110");
        given(sigunguRepository.existsBy(sigunguCode)).willReturn(true);
        given(populationRepository.findBy(sigunguCode))
                .willReturn(Optional.of(Population.of(sigunguCode, 140_000)));

        // when
        Integer result = populationQueryService.getPopulation(sigunguCode);

        // then
        assertThat(result).isEqualTo(140_000);
    }

    @Test
    @DisplayName("시군구는 있지만 인구 데이터가 없으면 null 을 돌려준다 - 데이터 없음")
    void returnsNullWhenSigunguExistsButPopulationMissing() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("11110");
        given(sigunguRepository.existsBy(sigunguCode)).willReturn(true);
        given(populationRepository.findBy(sigunguCode)).willReturn(Optional.empty());

        // when
        Integer result = populationQueryService.getPopulation(sigunguCode);

        // then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("존재하지 않는 시군구면 ADDRESS_CODE_NOT_FOUND 를 던지고 인구를 조회하지 않는다 - 코드 없음")
    void throwsWhenSigunguDoesNotExist() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("99999");
        given(sigunguRepository.existsBy(sigunguCode)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> populationQueryService.getPopulation(sigunguCode))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
        then(populationRepository).shouldHaveNoInteractions();
    }
}
