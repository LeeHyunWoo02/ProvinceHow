package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobVacancyRaw;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaraminJobVacancyParserTest {

    private final SaraminJobVacancyParser parser =
            new SaraminJobVacancyParser(new SaraminResponseReader(new ObjectMapper()));
    private final SaraminApiSpecFile.Response spec = SaraminApiSpecFile.defaults().response();

    @Test
    @DisplayName("풍부한 필드가 있는 응답에서 카드 필드를 모두 뽑는다")
    void extractsRichCardFields() {
        // given
        String body = """
                {
                  "jobs": {
                    "count": 1, "start": 0, "total": "1",
                    "job": {
                      "id": "46203390",
                      "url": "https://www.saramin.co.kr/zf_user/jobs/relay/view?rec_idx=46203390",
                      "active": 1,
                      "posting-timestamp": "1754006400",
                      "expiration-timestamp": "1756598400",
                      "company": { "detail": { "name": "스매시", "href": "https://company" } },
                      "position": {
                        "title": "백엔드 개발자",
                        "location": { "code": "101000", "name": "서울 > 강남구" },
                        "job-code": { "code": "84", "name": "웹개발" },
                        "experience-level": { "name": "신입" },
                        "required-education-level": { "name": "대졸" },
                        "job-type": { "name": "정규직" }
                      },
                      "salary": { "name": "회사내규에 따름" }
                    }
                  }
                }
                """;

        // when
        List<SaraminJobVacancyRaw> raws = parser.parse(body, spec);

        // then
        assertThat(raws).hasSize(1);
        SaraminJobVacancyRaw raw = raws.get(0);
        assertThat(raw.id()).isEqualTo("46203390");
        assertThat(raw.title()).isEqualTo("백엔드 개발자");
        assertThat(raw.companyName()).isEqualTo("스매시");
        assertThat(raw.detailUrl()).contains("rec_idx=46203390");
        assertThat(raw.regionName()).isEqualTo("서울 > 강남구");
        assertThat(raw.jobName()).isEqualTo("웹개발");
        assertThat(raw.salaryText()).isEqualTo("회사내규에 따름");
        assertThat(raw.experienceText()).isEqualTo("신입");
        assertThat(raw.educationText()).isEqualTo("대졸");
        assertThat(raw.employmentType()).isEqualTo("정규직");
        assertThat(raw.active()).isEqualTo("1");
        assertThat(raw.expirationTimestamp()).isEqualTo("1756598400");
    }

    @Test
    @DisplayName("목록이 배열이 아니라 단일 객체로 와도 읽는다")
    void parsesSingleObject() {
        String body = """
                { "jobs": { "total": "1", "job": {
                    "id": "7", "position": { "title": "QA" } } } }
                """;

        List<SaraminJobVacancyRaw> raws = parser.parse(body, spec);

        assertThat(raws).hasSize(1);
        assertThat(raws.get(0).title()).isEqualTo("QA");
    }

    @Test
    @DisplayName("목록이 비어 있으면 빈 리스트")
    void parsesEmptyList() {
        assertThat(parser.parse("{ \"jobs\": { \"total\": \"0\" } }", spec)).isEmpty();
    }

    @Test
    @DisplayName("jobs 오브젝트가 없으면 예외를 던진다")
    void throwsWhenRootMissing() {
        assertThatThrownBy(() -> parser.parse("{}", spec))
                .isInstanceOf(SaraminApiException.class);
    }
}
