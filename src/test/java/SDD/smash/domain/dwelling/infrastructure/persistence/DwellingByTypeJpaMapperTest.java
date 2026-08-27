package SDD.smash.domain.dwelling.infrastructure.persistence;

import SDD.smash.domain.dwelling.domain.model.DwellingTypeStat;
import SDD.smash.domain.dwelling.domain.model.HousingType;
import SDD.smash.domain.dwelling.domain.model.RentStat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DwellingByTypeJpaMapperTest {

    private static final String SIGUNGU_CODE = "11110";

    private final DwellingByTypeJpaMapper mapper = new DwellingByTypeJpaMapper();

    @Test
    @DisplayName("도메인 → 엔티티 → 도메인 왕복 변환이 값을 보존한다")
    void preservesValuesOnRoundTrip() {
        // given
        DwellingTypeStat origin = new DwellingTypeStat(
                HousingType.APARTMENT,
                RentStat.of(62.5, 60),
                RentStat.of(31_000.0, 30_000));

        // when
        DwellingTypeStat restored = mapper.toDomain(mapper.toJpaEntity(SIGUNGU_CODE, origin));

        // then
        assertThat(restored).isEqualTo(origin);
    }

    @Test
    @DisplayName("평균·중앙값이 없는 유형도 null 그대로 왕복한다")
    void preservesNullStatsOnRoundTrip() {
        // given - 실거래가 없는 유형은 평균·중앙값이 모두 비는 것이 정상이다
        DwellingTypeStat origin = new DwellingTypeStat(
                HousingType.DETACHED_HOUSE, RentStat.EMPTY, RentStat.of(null, null));

        // when
        DwellingTypeStat restored = mapper.toDomain(mapper.toJpaEntity(SIGUNGU_CODE, origin));

        // then
        assertThat(restored).isEqualTo(origin);
        assertThat(restored.monthly().hasMedian()).isFalse();
        assertThat(restored.jeonse().average()).isNull();
    }

    @Test
    @DisplayName("중앙값만 있고 평균이 없는 경우도 보존된다")
    void preservesMedianOnlyStat() {
        // given
        DwellingTypeStat origin = new DwellingTypeStat(
                HousingType.MULTIPLEX_HOUSE, RentStat.of(null, 45), RentStat.EMPTY);

        // when
        DwellingByTypeJpaEntity entity = mapper.toJpaEntity(SIGUNGU_CODE, origin);

        // then
        assertThat(entity.getSigunguCode()).isEqualTo(SIGUNGU_CODE);
        assertThat(entity.getHousingType()).isEqualTo(HousingType.MULTIPLEX_HOUSE);
        assertThat(entity.getMonthAvg()).isNull();
        assertThat(entity.getMonthMid()).isEqualTo(45);
        assertThat(mapper.toDomain(entity)).isEqualTo(origin);
    }
}
