package SDD.smash.domain.address.infrastructure.batch;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.infrastructure.batch.dto.PopulationUpsertRow;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class PopulationSnapshotBatchMapperTest {

    @Test
    @DisplayName("Upsert 파라미터로 옮기면 인구수가 숫자만 담긴 문자열이 된다")
    void convertsSnapshotToUpsertRow() {
        // given
        PopulationSnapshot snapshot =
                PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, YearMonth.of(2026, 6));

        // when
        PopulationUpsertRow row = PopulationSnapshotBatchMapper.toUpsertRow(snapshot);

        // then
        assertThat(row.getSigunguCode()).isEqualTo("11110");
        assertThat(row.getPopulation()).isEqualTo("140000");
    }

    @Test
    @DisplayName("통계 기준월은 옮겨지지 않는다 - 저장할 컬럼이 없다")
    void dropsStatisticsMonthBecauseSchemaHasNoColumn() {
        // given
        PopulationSnapshot june =
                PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, YearMonth.of(2026, 6));
        PopulationSnapshot may =
                PopulationSnapshot.of(SigunguCode.of("11110"), 140_000, YearMonth.of(2026, 5));

        // when
        PopulationUpsertRow fromJune = PopulationSnapshotBatchMapper.toUpsertRow(june);
        PopulationUpsertRow fromMay = PopulationSnapshotBatchMapper.toUpsertRow(may);

        // then - 기준월이 달라도 적재 파라미터는 같다(= 같은 행을 upsert 한다)
        assertThat(fromJune.getSigunguCode()).isEqualTo(fromMay.getSigunguCode());
        assertThat(fromJune.getPopulation()).isEqualTo(fromMay.getPopulation());
    }
}
