package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobSampleRaw;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사람인 채용정보 응답(JSON)에서 <b>프로필 집계용 원자료</b>(연봉·경력·업종)를 뽑는 파서.
 *
 * <p>응답 골격 파싱은 {@link SaraminResponseReader} 가 하고, 이 파서는 job 노드를
 * {@link SaraminJobSampleRaw} 로 옮기는 일만 맡는다. 프로필 필드 경로는 사람인 공식 응답
 * 구조상 안정적이라 이 파서의 상수로 둔다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaraminJobSampleParser {

    private static final String PATH_SALARY_NAME = "salary.name";
    private static final String PATH_EXPERIENCE_CODE = "position.experience-level.code";
    private static final String PATH_INDUSTRY_NAME = "position.industry.name";

    private final SaraminResponseReader responseReader;

    /**
     * @return 프로필 집계용 원문 표본 목록. 목록이 없으면 빈 리스트.
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public List<SaraminJobSampleRaw> parse(String body, SaraminApiSpecFile.Response spec) {
        return responseReader.read(body, spec).jobNodes().stream()
                .map(this::toRaw)
                .toList();
    }

    private SaraminJobSampleRaw toRaw(JsonNode node) {
        return new SaraminJobSampleRaw(
                SaraminResponseReader.textAt(node, PATH_SALARY_NAME),
                SaraminResponseReader.textAt(node, PATH_EXPERIENCE_CODE),
                SaraminResponseReader.textAt(node, PATH_INDUSTRY_NAME));
    }
}
