package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetJobPostingRaw;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorknetJobPostingParserTest {

    private final WorknetJobPostingParser parser = new WorknetJobPostingParser(new XmlMapper());
    private final WorknetApiSpecFile.Response spec = WorknetApiSpecFile.defaults().response();

    @Test
    @DisplayName("정상 응답에서 전체 건수와 공고 목록을 읽는다")
    void parsesTotalAndPostingsFromNormalResponse() throws IOException {
        // given
        String body = fixture("wanted-list-page1.xml");

        // when
        WorknetJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        assertThat(page.items().get(0).postingId()).isEqualTo("KJAU002608130001");
        assertThat(page.items().get(0).regionCodes()).containsExactly("11110");
        assertThat(page.items().get(0).jobCodes()).containsExactly("011");
    }

    @Test
    @DisplayName("한 필드에 여러 코드가 들어오면 구분자로 나눈다")
    void splitsMultipleCodesInOneField() throws IOException {
        // given
        String body = fixture("wanted-list-page1.xml");

        // when
        WorknetJobPostingRaw second = parser.parse(body, spec).items().get(1);

        // then
        assertThat(second.regionCodes()).containsExactly("11140", "11170");
        assertThat(second.jobCodes()).containsExactly("011", "012");
    }

    @Test
    @DisplayName("목록이 한 건이면 배열이 아니라 단일 요소로 와도 읽는다")
    void parsesSingleItemResponse() {
        // given
        String body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <wantedRoot>
                    <total>1</total>
                    <wanted>
                        <wantedAuthNo>KJAU1</wantedAuthNo>
                        <regionCd>26110</regionCd>
                        <jobsCd>013</jobsCd>
                    </wanted>
                </wantedRoot>
                """;

        // when
        WorknetJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).postingId()).isEqualTo("KJAU1");
    }

    @Test
    @DisplayName("빈 응답이면 전체 건수 0에 목록도 비어 있다")
    void parsesEmptyResponse() {
        // given
        String body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <wantedRoot><total>0</total></wantedRoot>
                """;

        // when
        WorknetJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("전체 건수 요소가 없으면 '알 수 없음'으로 둔다")
    void marksTotalUnknownWhenElementMissing() {
        // given
        String body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <wantedRoot>
                    <wanted><wantedAuthNo>KJAU1</wantedAuthNo><regionCd>11110</regionCd><jobsCd>011</jobsCd></wanted>
                </wantedRoot>
                """;

        // when
        WorknetJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.total()).isEqualTo(JobPostingPage.UNKNOWN_TOTAL);
    }

    @Test
    @DisplayName("messageCd 가 있는 오류 응답이면 예외를 던지고 코드를 보존한다")
    void throwsWithMessageCodeOnApiErrorResponse() throws IOException {
        // given - 인증키 없이 실제 엔드포인트를 찔러 받은 응답 원문
        String body = fixture("invalid-auth-key.xml");

        // when & then
        assertThatThrownBy(() -> parser.parse(body, spec))
                .isInstanceOf(WorknetApiException.class)
                .hasMessageContaining("002")
                .extracting(e -> ((WorknetApiException) e).messageCode())
                .isEqualTo("002");
    }

    @Test
    @DisplayName("본문이 비어 있으면 예외를 던진다")
    void throwsOnBlankBody() {
        assertThatThrownBy(() -> parser.parse("   ", spec))
                .isInstanceOf(WorknetApiException.class);
    }

    @Test
    @DisplayName("XML 이 아니면 파싱 실패 예외를 던진다")
    void throwsOnMalformedBody() {
        assertThatThrownBy(() -> parser.parse("<<<not xml", spec))
                .isInstanceOf(WorknetApiException.class);
    }

    @Test
    @DisplayName("설정으로 필드명을 바꾸면 그 필드에서 코드를 읽는다")
    void readsCodesFromConfiguredFieldNames() {
        // given - 인증키 발급 후 실제 필드명이 다르면 설정만 바꾸면 된다
        WorknetApiSpecFile.Response custom = new WorknetApiSpecFile.Response(
                "wanted", "total", "message", "messageCd", "wantedAuthNo",
                List.of("workRegionCd"), List.of("keco3"), List.of(";"));
        String body = """
                <?xml version="1.0" encoding="UTF-8"?>
                <wantedRoot>
                    <total>1</total>
                    <wanted>
                        <wantedAuthNo>KJAU9</wantedAuthNo>
                        <workRegionCd>41110;41130</workRegionCd>
                        <keco3>021</keco3>
                    </wanted>
                </wantedRoot>
                """;

        // when
        WorknetJobPostingRaw raw = parser.parse(body, custom).items().get(0);

        // then
        assertThat(raw.regionCodes()).containsExactly("41110", "41130");
        assertThat(raw.jobCodes()).containsExactly("021");
    }

    private String fixture(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/fixtures/worknet/" + name)) {
            assertThat(in).as("fixture %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
