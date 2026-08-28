package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.job.infrastructure.batch.dto.RegionJobStatisticsCsvRow;
import SDD.smash.domain.job.infrastructure.batch.dto.RegionJobStatisticsUpsertRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaRepository;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

/**
 * 고용행정통계 시드 배치 단위 테스트.
 *
 * <p>Spring 컨텍스트를 띄우지 않고 {@code BatchConfig} 를 직접 {@code new} 해서 운영에 쓰이는
 * Reader/Processor 빈 메서드를 그대로 호출한다. JobRepository/트랜잭션매니저/DataSource 는
 * Reader·Processor 조립에 관여하지 않으므로 {@code null} 을 넘긴다.
 */
@ExtendWith(MockitoExtension.class)
class RegionJobStatisticsBatchConfigTest {

    @TempDir
    Path tempDir;

    @Mock
    AddressQueryService addressQueryService;

    @Mock
    JobCodeTopJpaRepository jobCodeTopJpaRepository;

    private RegionJobStatisticsBatchConfig config;

    @BeforeEach
    void setUp() {
        config = new RegionJobStatisticsBatchConfig(
                null, null, addressQueryService, jobCodeTopJpaRepository, null);
    }

    // ---------------------------------------------------------------- Reader

    @Test
    @DisplayName("10개 열 중 적재 대상 8개만 읽는다 (sido_code·sigungu_name 은 버린다)")
    void readsOnlyPersistedColumns() throws Exception {
        Path csv = writeCsv("""
                sigungu_code,sido_code,sigungu_name,job_top_code,year_month,job_openings,job_seekers,placements,valid_openings,valid_seekers
                11110,11,종로구,01,2023-08,179,210,54,200,886
                """);

        List<RegionJobStatisticsCsvRow> rows = readAll(csv);

        assertThat(rows).containsExactly(new RegionJobStatisticsCsvRow(
                "11110", "01", "2023-08", 179L, 210L, 54L, 200L, 886L));
    }

    @Test
    @DisplayName("파일이 없으면 예외 없이 0건으로 끝난다")
    void readsNothingWhenFileIsMissing() throws Exception {
        List<RegionJobStatisticsCsvRow> rows = readAll(tempDir.resolve("no-such-file.csv"));

        assertThat(rows).isEmpty();
    }

    @Test
    @DisplayName("헤더만 있는 빈 파일도 0건으로 끝난다")
    void readsNothingWhenFileHasNoDataRow() throws Exception {
        Path csv = writeCsv("""
                sigungu_code,sido_code,sigungu_name,job_top_code,year_month,job_openings,job_seekers,placements,valid_openings,valid_seekers
                """);

        assertThat(readAll(csv)).isEmpty();
    }

    // ------------------------------------------------------------- Processor

    @Test
    @DisplayName("등록된 시군구·직종 대분류면 Upsert 행으로 바꾼다")
    void convertsKnownCodesIntoUpsertRow() throws Exception {
        givenKnownSigungu("11110");
        givenKnownJobTopCode("01");

        RegionJobStatisticsUpsertRow row = process(csvRow("11110", "01", "2026-07"));

        assertThat(row).isNotNull();
        assertThat(row.getSigunguCode()).isEqualTo("11110");
        assertThat(row.getJobTopCode()).isEqualTo("01");
        assertThat(row.getStatMonth()).isEqualTo("2026-07");
        assertThat(row.getValidOpenings()).isEqualTo(200L);
    }

    @Test
    @DisplayName("한 자리로 온 직종 대분류는 앞에 0을 채워 매칭한다")
    void padsSingleDigitJobTopCode() throws Exception {
        givenKnownSigungu("11110");
        givenKnownJobTopCode("01");

        assertThat(process(csvRow("11110", "1", "2026-07"))).isNotNull();
    }

    @Test
    @DisplayName("등록되지 않은 시군구 행은 적재하지 않는다")
    void skipsUnknownSigunguCode() throws Exception {
        givenKnownSigungu("11110");

        assertThat(process(csvRow("99999", "01", "2026-07"))).isNull();
    }

    @Test
    @DisplayName("등록되지 않은 직종 대분류 행은 적재하지 않는다")
    void skipsUnknownJobTopCode() throws Exception {
        givenKnownSigungu("11110");
        givenKnownJobTopCode("01");

        assertThat(process(csvRow("11110", "99", "2026-07"))).isNull();
    }

    @Test
    @DisplayName("기준월 형식이 깨진 행은 적재하지 않는다")
    void skipsMalformedMonth() throws Exception {
        givenKnownSigungu("11110");
        givenKnownJobTopCode("01");

        assertThat(process(csvRow("11110", "01", "2026/07"))).isNull();
    }

    @Test
    @DisplayName("지표가 비었거나 음수인 행은 적재하지 않는다")
    void skipsMissingOrNegativeMeasure() throws Exception {
        givenKnownSigungu("11110");
        givenKnownJobTopCode("01");

        RegionJobStatisticsCsvRow missing = new RegionJobStatisticsCsvRow(
                "11110", "01", "2026-07", null, 210L, 54L, 200L, 886L);
        RegionJobStatisticsCsvRow negative = new RegionJobStatisticsCsvRow(
                "11110", "01", "2026-07", 179L, 210L, 54L, -1L, 886L);

        assertThat(process(missing)).isNull();
        assertThat(process(negative)).isNull();
    }

    // ------------------------------------------------------------------ 헬퍼

    private void givenKnownSigungu(String... codes) {
        given(addressQueryService.getAllSigunguCodes())
                .willReturn(java.util.Arrays.stream(codes).map(SigunguCode::of).toList());
    }

    private void givenKnownJobTopCode(String... codes) {
        given(jobCodeTopJpaRepository.findAll())
                .willReturn(java.util.Arrays.stream(codes)
                        .map(code -> JobCodeTopJpaEntity.builder().code(code).name(code).build())
                        .toList());
    }

    private RegionJobStatisticsCsvRow csvRow(String sigunguCode, String jobTopCode, String yearMonth) {
        return new RegionJobStatisticsCsvRow(sigunguCode, jobTopCode, yearMonth, 179L, 210L, 54L, 200L, 886L);
    }

    private RegionJobStatisticsUpsertRow process(RegionJobStatisticsCsvRow row) throws Exception {
        return config.regionJobStatisticsProcessor().process(row);
    }

    private Path writeCsv(String content) throws IOException {
        Path path = tempDir.resolve("eis_job_stats.csv");
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private List<RegionJobStatisticsCsvRow> readAll(Path csv) throws Exception {
        ReflectionTestUtils.setField(config, "filePath", csv.toString());
        FlatFileItemReader<RegionJobStatisticsCsvRow> reader = config.regionJobStatisticsCsvReader();

        List<RegionJobStatisticsCsvRow> rows = new ArrayList<>();
        reader.open(new ExecutionContext());
        try {
            RegionJobStatisticsCsvRow row;
            while ((row = reader.read()) != null) {
                rows.add(row);
            }
        } finally {
            reader.close();
        }
        return rows;
    }
}
