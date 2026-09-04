package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobSampleRaw;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SaraminJobSampleParserTest {

    private final SaraminJobSampleParser parser =
            new SaraminJobSampleParser(new SaraminResponseReader(new ObjectMapper()));
    private final SaraminApiSpecFile.Response spec = SaraminApiSpecFile.defaults().response();

    @Test
    @DisplayName("연봉·경력·업종을 표본 원자료로 뽑는다")
    void extractsProfileFields() {
        // given
        String body = """
                {
                  "jobs": { "count": 2, "start": 0, "total": "2", "job": [
                    { "salary": { "code": "9", "name": "3,000~4,000만원" },
                      "position": { "experience-level": { "code": "1", "name": "신입" },
                                    "industry": { "code": "3", "name": "IT·웹·통신" } } },
                    { "salary": { "code": "0", "name": "회사내규에 따름" },
                      "position": { "experience-level": { "code": "2", "name": "경력" },
                                    "industry": { "name": "금융·보험" } } }
                  ] }
                }
                """;

        // when
        List<SaraminJobSampleRaw> raws = parser.parse(body, spec);

        // then
        assertThat(raws).hasSize(2);
        assertThat(raws.get(0).salaryName()).isEqualTo("3,000~4,000만원");
        assertThat(raws.get(0).experienceCode()).isEqualTo("1");
        assertThat(raws.get(0).industryName()).isEqualTo("IT·웹·통신");
        assertThat(raws.get(1).salaryName()).isEqualTo("회사내규에 따름");
        assertThat(raws.get(1).experienceCode()).isEqualTo("2");
        assertThat(raws.get(1).industryName()).isEqualTo("금융·보험");
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
