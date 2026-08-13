package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.infrastructure.batch.dto.JobCodeMiddleCsvRow;
import SDD.smash.domain.job.infrastructure.batch.dto.JobCodeTopCsvRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeMiddleJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.FlatFileParseException;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 직종 대/중분류 시드 CSV Reader 단위 테스트.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. {@code BatchConfig} 를 직접 {@code new} 해서
 * 실제 운영에 쓰이는 Reader 빈 메서드를 그대로 호출한다 — Reader 조립을 테스트에서 다시
 * 쓰면 운영 설정이 바뀌어도 테스트가 통과해 버리기 때문이다.
 * Reader 조립에는 JobRepository/Repository 가 관여하지 않으므로 생성자에는 {@code null} 을 넘긴다.
 */
class JobCodeCsvReaderTest {

    private static final Path REAL_TOP_CSV = Path.of("data", "static", "level_top.csv");
    private static final Path REAL_MIDDLE_CSV = Path.of("data", "static", "level_middle.csv");

    @TempDir
    Path tempDir;

    // ---------------------------------------------------------------- 중분류

    @Test
    @DisplayName("이름에 쉼표가 있어도 큰따옴표로 감싸면 3개 열로 파싱된다")
    void parsesQuotedMiddleNameContainingComma() throws Exception {
        // given
        Path csv = writeCsv("middle.csv", """
                code,name,upstream_code
                031,"대학교수, 학교 및 유치원 교사",03
                102,"전기·전자 기기 설치·수리(사무용,가전제품,기타)",10
                """);

        // when
        List<JobCodeMiddleCsvRow> rows = readAll(middleReader(csv));

        // then
        assertThat(rows).containsExactly(
                new JobCodeMiddleCsvRow("031", "대학교수, 학교 및 유치원 교사", "03"),
                new JobCodeMiddleCsvRow("102", "전기·전자 기기 설치·수리(사무용,가전제품,기타)", "10"));
    }

    @Test
    @DisplayName("따옴표 없이 쉼표가 들어간 중분류 행은 skip 되지 않고 파싱 예외로 실패한다")
    void failsWhenMiddleRowHasWrongColumnCount() throws Exception {
        // given
        Path csv = writeCsv("middle-broken.csv", """
                code,name,upstream_code
                031,대학교수, 학교 및 유치원 교사,03
                """);
        FlatFileItemReader<JobCodeMiddleCsvRow> reader = middleReader(csv);

        // when & then
        assertThatThrownBy(() -> readAll(reader))
                .isInstanceOf(FlatFileParseException.class);
    }

    @Test
    @DisplayName("열이 모자란 중분류 행도 파싱 예외로 실패한다")
    void failsWhenMiddleRowHasTooFewColumns() throws Exception {
        // given
        Path csv = writeCsv("middle-short.csv", """
                code,name,upstream_code
                031,대학교수
                """);
        FlatFileItemReader<JobCodeMiddleCsvRow> reader = middleReader(csv);

        // when & then
        assertThatThrownBy(() -> readAll(reader))
                .isInstanceOf(FlatFileParseException.class);
    }

    @Test
    @DisplayName("중분류 코드의 앞자리 0 과 영문 섞인 코드가 엔티티 변환 후에도 보존된다")
    void keepsLeadingZeroOfMiddleCode() throws Exception {
        // given
        Path csv = writeCsv("middle-zero.csv", """
                code,name,upstream_code
                011,행정·경영·금융·보험 관리직,01
                01A,안내·접수·고객응대 사무,01
                102,"전기·전자 기기 설치·수리(사무용,가전제품,기타)",10
                """);

        // when
        List<JobCodeMiddleCsvRow> rows = readAll(middleReader(csv));
        List<String> codes = rows.stream()
                .map(row -> JobCsvMapper.toMiddleJpaEntity(row, row.upstream()))
                .map(JobCodeMiddleJpaEntity::getCode)
                .toList();

        // then
        assertThat(rows).extracting(JobCodeMiddleCsvRow::code)
                .containsExactly("011", "01A", "102");
        assertThat(codes).containsExactly("011", "01A", "102");
    }

    // ---------------------------------------------------------------- 대분류

    @Test
    @DisplayName("대분류 CSV 는 2개 열로 파싱되고 코드 앞자리 0 이 보존된다")
    void parsesTopRowsAndKeepsLeadingZero() throws Exception {
        // given
        Path csv = writeCsv("top.csv", """
                code,name
                01,경영·사무·금융·보험직
                10,"설치·정비·생산직(기계,금속·재료)"
                """);

        // when
        List<JobCodeTopCsvRow> rows = readAll(topReader(csv));
        List<String> codes = rows.stream()
                .map(JobCsvMapper::toTopJpaEntity)
                .map(JobCodeTopJpaEntity::getCode)
                .toList();

        // then
        assertThat(rows).containsExactly(
                new JobCodeTopCsvRow("01", "경영·사무·금융·보험직"),
                new JobCodeTopCsvRow("10", "설치·정비·생산직(기계,금속·재료)"));
        assertThat(codes).containsExactly("01", "10");
    }

    @Test
    @DisplayName("따옴표 없이 쉼표가 들어간 대분류 행은 파싱 예외로 실패한다")
    void failsWhenTopRowHasWrongColumnCount() throws Exception {
        // given
        Path csv = writeCsv("top-broken.csv", """
                code,name
                10,설치·정비·생산직,기계·금속
                """);
        FlatFileItemReader<JobCodeTopCsvRow> reader = topReader(csv);

        // when & then
        assertThatThrownBy(() -> readAll(reader))
                .isInstanceOf(FlatFileParseException.class);
    }

    // ---------------------------------------------------------------- 실제 시드 파일

    @Test
    @DisplayName("실제 시드 CSV 를 UTF-8 로 읽으면 모든 행이 정해진 열 수로 파싱되고 한글이 깨지지 않는다")
    void readsRealSeedCsvFiles() throws Exception {
        // given
        assertThat(REAL_TOP_CSV).exists();
        assertThat(REAL_MIDDLE_CSV).exists();

        // when
        List<JobCodeTopCsvRow> tops = readAll(topReader(REAL_TOP_CSV));
        List<JobCodeMiddleCsvRow> middles = readAll(middleReader(REAL_MIDDLE_CSV));

        // then
        assertThat(tops).hasSize(13);
        assertThat(middles).hasSize(114);
        assertThat(tops).allSatisfy(row -> {
            assertThat(row.code()).isNotBlank();
            assertThat(row.name()).isNotBlank();
        });
        assertThat(middles).allSatisfy(row -> {
            assertThat(row.code()).isNotBlank();
            assertThat(row.name()).isNotBlank();
            assertThat(row.upstream()).isNotBlank();
        });
        assertThat(middles).extracting(JobCodeMiddleCsvRow::name)
                .contains("대학교수, 학교 및 유치원 교사",
                        "전기·전자 기기 설치·수리(사무용,가전제품,기타)");
    }

    @Test
    @DisplayName("실제 시드 CSV 의 중분류 upstream_code 는 전부 대분류 코드 집합에 포함된다")
    void everyMiddleUpstreamCodeExistsInTopCodes() throws Exception {
        // given
        Set<String> topCodes = readAll(topReader(REAL_TOP_CSV)).stream()
                .map(row -> JobCsvMapper.toTopJpaEntity(row).getCode())
                .collect(Collectors.toSet());

        // when
        List<String> upstreamCodes = readAll(middleReader(REAL_MIDDLE_CSV)).stream()
                .map(JobCodeMiddleCsvRow::upstream)
                .distinct()
                .toList();

        // then
        assertThat(topCodes).hasSize(13);
        assertThat(upstreamCodes).isNotEmpty();
        assertThat(topCodes).containsAll(upstreamCodes);
    }

    @Test
    @DisplayName("실제 중분류 시드 CSV 의 코드는 대분류 코드로 시작하는 3자리 코드다")
    void realMiddleCodesKeepLeadingZeros() throws Exception {
        // when
        List<JobCodeMiddleCsvRow> middles = readAll(middleReader(REAL_MIDDLE_CSV));

        // then
        assertThat(middles).allSatisfy(row -> {
            String code = JobCsvMapper.toMiddleJpaEntity(row, row.upstream()).getCode();
            assertThat(code).hasSize(3);
            assertThat(code).startsWith(row.upstream());
        });
        assertThat(middles).extracting(JobCodeMiddleCsvRow::code).contains("011", "01A", "11A");
    }

    // ---------------------------------------------------------------- helper

    private FlatFileItemReader<JobCodeMiddleCsvRow> middleReader(Path csv) {
        JobCodeMiddleBatchConfig config = new JobCodeMiddleBatchConfig(null, null, null, null);
        ReflectionTestUtils.setField(config, "filePath", csv.toString());
        return config.jcMiddleCsvReader();
    }

    private FlatFileItemReader<JobCodeTopCsvRow> topReader(Path csv) {
        JobCodeTopBatchConfig config = new JobCodeTopBatchConfig(null, null, null);
        ReflectionTestUtils.setField(config, "filePath", csv.toString());
        return config.jcTopCsvReader();
    }

    private Path writeCsv(String fileName, String content) throws IOException {
        Path path = tempDir.resolve(fileName);
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private <T> List<T> readAll(FlatFileItemReader<T> reader) throws Exception {
        reader.open(new ExecutionContext());
        try {
            List<T> items = new ArrayList<>();
            T item;
            while ((item = reader.read()) != null) {
                items.add(item);
            }
            return items;
        } finally {
            reader.close();
        }
    }
}
