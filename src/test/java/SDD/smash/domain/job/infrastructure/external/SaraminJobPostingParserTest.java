package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobPostingRaw;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaraminJobPostingParserTest {

    private final SaraminJobPostingParser parser = new SaraminJobPostingParser(new ObjectMapper());
    private final SaraminApiSpecFile.Response spec = SaraminApiSpecFile.defaults().response();

    @Test
    @DisplayName("정상 응답에서 문자열 total 과 job[] 을 읽고 id/지역/직종 코드를 뽑는다")
    void parsesTotalAndJobsFromNormalResponse() {
        // given - total 은 문자열, 지역/직종은 중첩 경로에 있다
        String body = """
                {
                  "jobs": {
                    "count": 2, "start": 0, "total": "3",
                    "job": [
                      { "id": "46203390",
                        "position": { "location": { "code": "101000", "name": "서울 > 강남구" },
                                      "job-code": { "code": "84" } } },
                      { "id": "46203391",
                        "position": { "location": { "code": "110000" },
                                      "job-code": { "code": "85" } } }
                    ]
                  }
                }
                """;

        // when
        SaraminJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.total()).isEqualTo(3);
        assertThat(page.items()).hasSize(2);
        SaraminJobPostingRaw first = page.items().get(0);
        assertThat(first.postingId()).isEqualTo("46203390");
        assertThat(first.regionCode()).isEqualTo("101000");
        assertThat(first.jobCode()).isEqualTo("84");
    }

    @Test
    @DisplayName("목록이 한 건이면 배열이 아니라 단일 객체로 와도 읽는다")
    void parsesSingleItemResponse() {
        // given
        String body = """
                {
                  "jobs": {
                    "count": 1, "start": 0, "total": "1",
                    "job": { "id": "99", "position": { "location": { "code": "111000" },
                                                       "job-code": { "code": "12" } } }
                  }
                }
                """;

        // when
        SaraminJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.items()).hasSize(1);
        assertThat(page.items().get(0).postingId()).isEqualTo("99");
        assertThat(page.items().get(0).regionCode()).isEqualTo("111000");
    }

    @Test
    @DisplayName("빈 응답이면 total 0 에 목록도 비어 있다")
    void parsesEmptyResponse() {
        // given
        String body = """
                { "jobs": { "count": 0, "start": 0, "total": "0" } }
                """;

        // when
        SaraminJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.total()).isZero();
        assertThat(page.items()).isEmpty();
    }

    @Test
    @DisplayName("total 필드가 없으면 '알 수 없음'으로 둔다")
    void marksTotalUnknownWhenFieldMissing() {
        // given
        String body = """
                { "jobs": { "job": [ { "id": "1", "position": { "location": { "code": "101000" },
                                                               "job-code": { "code": "84" } } } ] } }
                """;

        // when
        SaraminJobPostingParser.ParsedPage page = parser.parse(body, spec);

        // then
        assertThat(page.total()).isEqualTo(JobPostingPage.UNKNOWN_TOTAL);
    }

    @Test
    @DisplayName("jobs 오브젝트가 없으면 예외를 던진다")
    void throwsWhenRootObjectMissing() {
        assertThatThrownBy(() -> parser.parse("{}", spec))
                .isInstanceOf(SaraminApiException.class)
                .hasMessageContaining("jobs");
    }

    @Test
    @DisplayName("오류 필드가 있으면 목록 파싱 전에 예외를 던진다")
    void throwsOnErrorFieldResponse() {
        // given
        String body = """
                { "error": "true", "message": "invalid access-key" }
                """;

        // when & then
        assertThatThrownBy(() -> parser.parse(body, spec))
                .isInstanceOf(SaraminApiException.class)
                .hasMessageContaining("invalid access-key");
    }

    @Test
    @DisplayName("본문이 비어 있으면 예외를 던진다")
    void throwsOnBlankBody() {
        assertThatThrownBy(() -> parser.parse("   ", spec))
                .isInstanceOf(SaraminApiException.class);
    }

    @Test
    @DisplayName("JSON 이 아니면 파싱 실패 예외를 던진다")
    void throwsOnMalformedBody() {
        assertThatThrownBy(() -> parser.parse("<<<not json", spec))
                .isInstanceOf(SaraminApiException.class);
    }

    @Test
    @DisplayName("설정으로 경로를 바꾸면 그 경로에서 코드를 읽는다")
    void readsCodesFromConfiguredPaths() {
        // given - job-mid-code 로 집계하고 싶을 때 경로만 바꾸면 된다
        SaraminApiSpecFile.Response custom = new SaraminApiSpecFile.Response(
                "jobs", "total", "job", "id",
                "position.location.code", "position.job-mid-code.code", "error", "message");
        String body = """
                {
                  "jobs": {
                    "total": "1",
                    "job": { "id": "7",
                             "position": { "location": { "code": "101000" },
                                           "job-code": { "code": "84" },
                                           "job-mid-code": { "code": "220" } } }
                  }
                }
                """;

        // when
        SaraminJobPostingRaw raw = parser.parse(body, custom).items().get(0);

        // then
        assertThat(raw.jobCode()).isEqualTo("220");
    }
}
