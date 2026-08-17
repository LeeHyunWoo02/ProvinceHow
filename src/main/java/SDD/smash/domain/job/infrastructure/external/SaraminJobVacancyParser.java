package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobVacancyRaw;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 사람인 채용정보 응답(JSON)에서 <b>표시용 카드 필드</b>를 뽑는 파서.
 * 집계용 {@link SaraminJobPostingParser}(id·코드만) 와 달리 제목·기업·연봉 등 풍부한 필드를 읽는다.
 * Jackson 2({@code com.fasterxml.jackson.databind.ObjectMapper}) 를 쓴다.
 *
 * <p>목록 위치({@code jobs.job})와 오류 판정은 스펙({@link SaraminApiSpecFile.Response})을 따르고,
 * 카드 표시 필드의 경로는 사람인 공식 응답 구조상 안정적이라 이 파서의 상수로 둔다
 * (집계에 쓰는 코드 필드처럼 매핑 대상이 아니라, 사람이 읽는 라벨을 그대로 옮기기만 한다).
 *
 * <p><b>응답 전체를 로그로 찍지 않는다.</b>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaraminJobVacancyParser {

    // 카드 표시 필드 경로(사람인 job[] 원소 기준). 점(.)은 중첩을 뜻한다.
    private static final String PATH_TITLE = "position.title";
    private static final String PATH_COMPANY = "company.detail.name";
    private static final String PATH_URL = "url";
    private static final String PATH_REGION_NAME = "position.location.name";
    private static final String PATH_JOB_NAME = "position.job-code.name";
    private static final String PATH_SALARY = "salary.name";
    private static final String PATH_EXPERIENCE = "position.experience-level.name";
    private static final String PATH_EDUCATION = "position.required-education-level.name";
    private static final String PATH_EMPLOYMENT = "position.job-type.name";
    private static final String FIELD_ID = "id";
    private static final String FIELD_ACTIVE = "active";
    private static final String FIELD_POSTING_TS = "posting-timestamp";
    private static final String FIELD_EXPIRATION_TS = "expiration-timestamp";

    private final ObjectMapper objectMapper;

    /**
     * @return 표시용 원문 카드 목록. 목록이 없으면 빈 리스트.
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public List<SaraminJobVacancyRaw> parse(String body, SaraminApiSpecFile.Response spec) {
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
        List<SaraminJobVacancyRaw> items = new ArrayList<>();
        if (list != null && !list.isMissingNode() && !list.isNull()) {
            if (list.isArray()) {
                list.forEach(node -> items.add(toRaw(node)));
            } else {
                items.add(toRaw(list));
            }
        }
        return List.copyOf(items);
    }

    private SaraminJobVacancyRaw toRaw(JsonNode node) {
        return new SaraminJobVacancyRaw(
                textOf(node, FIELD_ID),
                textAt(node, PATH_TITLE),
                textAt(node, PATH_COMPANY),
                textOf(node, PATH_URL),
                textAt(node, PATH_REGION_NAME),
                textAt(node, PATH_JOB_NAME),
                textAt(node, PATH_SALARY),
                textAt(node, PATH_EXPERIENCE),
                textAt(node, PATH_EDUCATION),
                textAt(node, PATH_EMPLOYMENT),
                textOf(node, FIELD_ACTIVE),
                textOf(node, FIELD_POSTING_TS),
                textOf(node, FIELD_EXPIRATION_TS));
    }

    /** 점(.)으로 표현된 중첩 경로를 따라 내려가 노드를 얻는다. 없으면 {@code null}. */
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
        return asText(at(base, dotPath));
    }

    private String textOf(JsonNode node, String field) {
        return asText((node == null) ? null : node.get(field));
    }

    private String asText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asText("").trim();
    }
}
