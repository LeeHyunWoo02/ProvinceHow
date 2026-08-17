package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobSampleRaw;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 사람인 채용정보 응답(JSON)에서 <b>프로필 집계용 원자료</b>(연봉·경력·업종)를 뽑는 파서.
 * Jackson 2 를 쓴다. 목록 위치({@code jobs.job})와 오류 판정은 스펙을 따르고, 프로필 필드 경로는
 * 사람인 공식 응답 구조상 안정적이라 이 파서의 상수로 둔다.
 *
 * <p><b>응답 전체를 로그로 찍지 않는다.</b>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaraminJobSampleParser {

    private static final String PATH_SALARY_NAME = "salary.name";
    private static final String PATH_EXPERIENCE_CODE = "position.experience-level.code";
    private static final String PATH_INDUSTRY_NAME = "position.industry.name";

    private final ObjectMapper objectMapper;

    /**
     * @return 프로필 집계용 원문 표본 목록. 목록이 없으면 빈 리스트.
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public List<SaraminJobSampleRaw> parse(String body, SaraminApiSpecFile.Response spec) {
        if (body == null || body.isBlank()) {
            throw new SaraminApiException("[saramin] 응답 본문이 비어 있다.");
        }

        JsonNode root;
        try {
            root = objectMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new SaraminApiException("[saramin] 응답 JSON 파싱 실패", e);
        }
        if (root == null || root.isMissingNode() || root.isNull()) {
            throw new SaraminApiException("[saramin] 응답 JSON 이 비어 있다.");
        }

        JsonNode error = at(root, spec.errorField());
        if (error != null && !error.isNull() && !error.isMissingNode()) {
            String message = textAt(root, spec.errorMessageField());
            throw new SaraminApiException("[saramin] API 오류 응답 message=" + message);
        }

        JsonNode jobsRoot = root.get(spec.rootField());
        if (jobsRoot == null || jobsRoot.isMissingNode() || jobsRoot.isNull()) {
            throw new SaraminApiException(
                    "[saramin] 응답에 '" + spec.rootField() + "' 오브젝트가 없다. 스펙 rootField 를 확인하라.");
        }

        JsonNode list = jobsRoot.get(spec.listField());
        List<SaraminJobSampleRaw> items = new ArrayList<>();
        if (list != null && !list.isMissingNode() && !list.isNull()) {
            if (list.isArray()) {
                list.forEach(node -> items.add(toRaw(node)));
            } else {
                items.add(toRaw(list));
            }
        }
        return List.copyOf(items);
    }

    private SaraminJobSampleRaw toRaw(JsonNode node) {
        return new SaraminJobSampleRaw(
                textAt(node, PATH_SALARY_NAME),
                textAt(node, PATH_EXPERIENCE_CODE),
                textAt(node, PATH_INDUSTRY_NAME));
    }

    private JsonNode at(JsonNode base, String dotPath) {
        if (base == null || dotPath == null || dotPath.isBlank()) {
            return null;
        }
        JsonNode current = base;
        for (String segment : dotPath.split("\\.")) {
            if (current == null || current.isMissingNode() || current.isNull()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }

    private String textAt(JsonNode base, String dotPath) {
        JsonNode value = at(base, dotPath);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asText("").trim();
    }
}
