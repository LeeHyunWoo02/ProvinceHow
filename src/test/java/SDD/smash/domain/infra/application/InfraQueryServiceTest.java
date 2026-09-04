package SDD.smash.domain.infra.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.infra.application.dto.IndustryCountView;
import SDD.smash.domain.infra.application.dto.MajorInfraSummaryView;
import SDD.smash.domain.infra.domain.model.IndustryCount;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.domain.model.MajorInfraSummary;
import SDD.smash.domain.infra.domain.model.RegionInfra;
import SDD.smash.domain.infra.domain.port.InfraMajorSummaryRepository;
import SDD.smash.domain.infra.domain.port.RegionInfraRepository;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

/**
 * InfraQueryService 유스케이스 테스트. 저장소 포트와 대상 컨텍스트 application Service 를 목킹한다.
 */
@ExtendWith(MockitoExtension.class)
class InfraQueryServiceTest {

    @Mock
    AddressQueryService addressQueryService;

    @Mock
    InfraMajorSummaryRepository infraMajorSummaryRepository;

    @Mock
    RegionInfraRepository regionInfraRepository;

    @InjectMocks
    InfraQueryService service;

    private static final SigunguCode CODE = SigunguCode.of("11110");

    @Test
    @DisplayName("대분류 요약을 findAllBy 단일 조회로 한 번만 가져온다")
    void queriesMajorSummariesWithSingleFindAllBy() {
        // given
        given(infraMajorSummaryRepository.findAllBy(CODE))
                .willReturn(List.of(new MajorInfraSummary(Major.LIFE, 3L, 70.0)));

        // when
        service.getMajorInfraSummaries(CODE);

        // then — 대분류마다 조회하지 않고 단일 조회 1회만 호출한다
        then(infraMajorSummaryRepository).should(times(1)).findAllBy(CODE);
        then(infraMajorSummaryRepository).shouldHaveNoMoreInteractions();
    }

    @Test
    @DisplayName("대분류 요약은 Major enum 순서(ordinal)로 정렬된다")
    void sortsMajorSummariesByMajorOrdinal() {
        // given — 저장소가 순서를 뒤섞어 반환해도 결과는 enum 순서를 따른다
        given(infraMajorSummaryRepository.findAllBy(CODE)).willReturn(List.of(
                new MajorInfraSummary(Major.LIFE, 1L, 10.0),
                new MajorInfraSummary(Major.HEALTH, 2L, 20.0),
                new MajorInfraSummary(Major.CULTURE, 3L, 30.0)));

        // when
        List<MajorInfraSummaryView> result = service.getMajorInfraSummaries(CODE);

        // then
        assertThat(result).extracting(MajorInfraSummaryView::major)
                .containsExactly(Major.HEALTH, Major.CULTURE, Major.LIFE);
    }

    @Test
    @DisplayName("적재된 대분류 데이터가 없으면 예외 없이 빈 목록을 반환한다")
    void returnsEmptyListWhenNoSummaryData() {
        // given
        given(infraMajorSummaryRepository.findAllBy(CODE)).willReturn(List.of());

        // when
        List<MajorInfraSummaryView> result = service.getMajorInfraSummaries(CODE);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 시군구 코드면 대분류 요약 조회 전에 예외를 던진다")
    void throwsWhenSigunguNotExistsOnMajorSummaries() {
        // given
        willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"))
                .given(addressQueryService).checkSigunguExistsOrThrow(CODE);

        // when & then
        assertThatThrownBy(() -> service.getMajorInfraSummaries(CODE))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
        then(infraMajorSummaryRepository).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("인프라 상세는 Aggregate 의 업종 목록을 뷰로 변환해 반환한다")
    void mapsIndustryCountsToViews() {
        // given
        RegionInfra regionInfra = RegionInfra.reconstitute(List.of(
                new IndustryCount(Major.LIFE, "미용실", 5, new BigDecimal("12.50"))));
        given(regionInfraRepository.findBy(CODE)).willReturn(regionInfra);

        // when
        List<IndustryCountView> result = service.getInfraDetails(CODE);

        // then
        assertThat(result).singleElement()
                .satisfies(view -> {
                    assertThat(view.major()).isEqualTo(Major.LIFE);
                    assertThat(view.industryName()).isEqualTo("미용실");
                    assertThat(view.count()).isEqualTo(5);
                    assertThat(view.ratio()).isEqualByComparingTo("12.50");
                });
    }

    @Test
    @DisplayName("존재하지 않는 시군구 코드면 인프라 상세 조회 전에 예외를 던진다")
    void throwsWhenSigunguNotExistsOnDetails() {
        // given
        willThrow(new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "유효하지 않은 시군구 코드"))
                .given(addressQueryService).checkSigunguExistsOrThrow(any());

        // when & then
        assertThatThrownBy(() -> service.getInfraDetails(CODE))
                .isInstanceOf(DomainException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.ADDRESS_CODE_NOT_FOUND);
        then(regionInfraRepository).shouldHaveNoInteractions();
    }
}
