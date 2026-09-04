package SDD.smash.domain.address.application;

import SDD.smash.domain.address.application.dto.RegionCodeView;
import SDD.smash.domain.address.application.dto.SidoView;
import SDD.smash.domain.address.application.dto.SigunguView;
import SDD.smash.domain.address.domain.model.RegionCode;
import SDD.smash.domain.address.domain.model.Sido;
import SDD.smash.domain.address.domain.model.Sigungu;
import SDD.smash.domain.address.domain.port.RegionCodeQuery;
import SDD.smash.domain.address.domain.port.SidoRepository;
import SDD.smash.domain.address.domain.port.SigunguRepository;
import SDD.smash.global.domain.model.SidoCode;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class AddressQueryServiceTest {

    @Mock
    SidoRepository sidoRepository;

    @Mock
    SigunguRepository sigunguRepository;

    @Mock
    RegionCodeQuery regionCodeQuery;

    @InjectMocks
    AddressQueryService addressQueryService;

    @Test
    @DisplayName("전체 시도를 뷰로 변환해 돌려준다")
    void returnsAllSidosAsViews() {
        // given
        given(sidoRepository.findAll()).willReturn(List.of(
                Sido.reconstitute(SidoCode.of("11"), "서울특별시"),
                Sido.reconstitute(SidoCode.of("26"), "부산광역시")));

        // when
        List<SidoView> result = addressQueryService.getAllSidos();

        // then
        assertThat(result).extracting(v -> v.code().value())
                .containsExactly("11", "26");
        assertThat(result).extracting(SidoView::name)
                .containsExactly("서울특별시", "부산광역시");
    }

    @Test
    @DisplayName("존재하는 시도의 시군구를 뷰로 변환해 돌려준다")
    void returnsSigungusWhenSidoExists() {
        // given
        SidoCode sidoCode = SidoCode.of("11");
        given(sidoRepository.existsBy(sidoCode)).willReturn(true);
        given(sigunguRepository.findAllBy(sidoCode)).willReturn(List.of(
                Sigungu.reconstitute(SigunguCode.of("11110"), "종로구", sidoCode),
                Sigungu.reconstitute(SigunguCode.of("11140"), "중구", sidoCode)));

        // when
        List<SigunguView> result = addressQueryService.getSigungusBySido(sidoCode);

        // then
        assertThat(result).extracting(v -> v.code().value())
                .containsExactly("11110", "11140");
    }

    @Test
    @DisplayName("없는 시도로 시군구를 조회하면 ADDRESS_CODE_NOT_FOUND 를 던지고 시군구를 조회하지 않는다")
    void throwsWhenSidoDoesNotExist() {
        // given
        SidoCode sidoCode = SidoCode.of("99");
        given(sidoRepository.existsBy(sidoCode)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> addressQueryService.getSigungusBySido(sidoCode))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
        then(sigunguRepository).should(never()).findAllBy(sidoCode);
    }

    @Test
    @DisplayName("전체 시군구 코드를 그대로 돌려준다")
    void returnsAllSigunguCodes() {
        // given
        List<SigunguCode> codes = List.of(SigunguCode.of("11110"), SigunguCode.of("11140"));
        given(sigunguRepository.findAllCodes()).willReturn(codes);

        // when
        List<SigunguCode> result = addressQueryService.getAllSigunguCodes();

        // then
        assertThat(result).isEqualTo(codes);
    }

    @Test
    @DisplayName("전체 지역코드를 뷰로 변환해 돌려준다")
    void returnsAllRegionCodesAsViews() {
        // given
        given(regionCodeQuery.findAll()).willReturn(List.of(
                new RegionCode(SidoCode.of("11"), "서울특별시", SigunguCode.of("11110"), "종로구")));

        // when
        List<RegionCodeView> result = addressQueryService.getAllRegionCodes();

        // then
        assertThat(result).singleElement()
                .satisfies(v -> {
                    assertThat(v.sidoName()).isEqualTo("서울특별시");
                    assertThat(v.sigunguCode().value()).isEqualTo("11110");
                    assertThat(v.sigunguName()).isEqualTo("종로구");
                });
    }

    @Test
    @DisplayName("지역코드가 있으면 뷰를 담은 Optional 을 돌려준다")
    void returnsRegionCodeWhenPresent() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("11110");
        given(regionCodeQuery.findBy(sigunguCode)).willReturn(Optional.of(
                new RegionCode(SidoCode.of("11"), "서울특별시", sigunguCode, "종로구")));

        // when
        Optional<RegionCodeView> result = addressQueryService.getRegionCode(sigunguCode);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().sigunguName()).isEqualTo("종로구");
    }

    @Test
    @DisplayName("지역코드가 없으면 빈 Optional 을 돌려준다")
    void returnsEmptyWhenRegionCodeMissing() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("99999");
        given(regionCodeQuery.findBy(sigunguCode)).willReturn(Optional.empty());

        // when
        Optional<RegionCodeView> result = addressQueryService.getRegionCode(sigunguCode);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하는 시군구면 예외 없이 통과한다")
    void passesWhenSigunguExists() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("11110");
        given(sigunguRepository.existsBy(sigunguCode)).willReturn(true);

        // when / then
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);
    }

    @Test
    @DisplayName("존재하지 않는 시군구면 ADDRESS_CODE_NOT_FOUND 를 던진다")
    void throwsWhenSigunguDoesNotExist() {
        // given
        SigunguCode sigunguCode = SigunguCode.of("99999");
        given(sigunguRepository.existsBy(sigunguCode)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> addressQueryService.checkSigunguExistsOrThrow(sigunguCode))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
    }
}
