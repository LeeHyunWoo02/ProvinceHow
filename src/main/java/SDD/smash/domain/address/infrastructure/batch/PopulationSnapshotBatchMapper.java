package SDD.smash.domain.address.infrastructure.batch;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.infrastructure.batch.dto.PopulationUpsertRow;

/**
 * 수집한 {@code PopulationSnapshot} → 인구 Upsert 파라미터 변환.
 *
 * <p>{@code PopulationUpsertRow.population} 이 {@code String} 인 것은 As-Is CSV 경로의 형태다.
 * {@code population_count} 컬럼은 {@code int} 이고 JDBC 가 문자열을 그대로 바인딩하므로
 * <b>숫자만 담긴 문자열</b>이어야 한다. {@code Integer.toString} 이 그것을 보장한다.
 *
 * <p><b>통계 기준월은 여기서 버려진다.</b> {@code population} 테이블에 {@code statistics_month} /
 * {@code collected_at} 컬럼이 없다. 스키마를 임의로 늘리지 않고 기준월은 JobParameter 와 로그로만 남긴다.
 */
public final class PopulationSnapshotBatchMapper {

    private PopulationSnapshotBatchMapper() {
    }

    public static PopulationUpsertRow toUpsertRow(PopulationSnapshot snapshot) {
        return PopulationUpsertRow.builder()
                .sigunguCode(snapshot.sigunguCode().value())
                .population(Integer.toString(snapshot.count()))
                .build();
    }
}
