package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobPostingRaw;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 사람인 채용정보 응답(JSON) 파서. Jackson 2({@code com.fasterxml.jackson.databind.ObjectMapper}) 를 쓴다.
 *
 * <p>응답 구조(핵심):
 * <pre>{@code
 * {
 *   "jobs": {
 *     "count": 10, "start": 0, "total": "12345",
 *     "job": [
 *       { "id": "1", "position": { "location": {"code":"101000"}, "job-code": {"code":"..."} } },
 *       ...
 *     ]
 *   }
 * }
 * }</pre>
 * {@code total} 은 <b>문자열</b>로 온다. {@code job} 은 목록이 한 건이면 배열이 아니라 단일 객체로 올 수도 있다.
 *
 * <p><b>응답 전체를 로그로 찍지 않는다.</b> 기업명·주소가 들어 있는 데다 양이 크다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaraminJobPostingParser {

    private final ObjectMapper objectMapper;

    /**
     * @return 원문 그대로의 공고 목록과 전체 건수
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public ParsedPage parse(String body, SaraminApiSpecFile.Response spec) {
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

        // 오류 필드가 있으면 목록 파싱 전에 실패로 끊는다(필드가 없으면 무시).
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

        int total = intValue(jobsRoot, spec.totalField());

        JsonNode list = jobsRoot.get(spec.listField());
        List<SaraminJobPostingRaw> items = new ArrayList<>();
        if (list != null && !list.isMissingNode() && !list.isNull()) {
            if (list.isArray()) {
                list.forEach(node -> items.add(toRaw(node, spec)));
            } else {
                items.add(toRaw(list, spec));
            }
        }
        return new ParsedPage(total, List.copyOf(items));
    }

    private SaraminJobPostingRaw toRaw(JsonNode node, SaraminApiSpecFile.Response spec) {
        String id = textOf(node, spec.postingIdField());
        String region = textAt(node, spec.regionCodePath());
        String job = textAt(node, spec.jobCodePath());
        return new SaraminJobPostingRaw(id, region, job);
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

    private int intValue(JsonNode node, String field) {
        String raw = textOf(node, field);
        if (raw == null || raw.isEmpty()) {
            return JobPostingPage.UNKNOWN_TOTAL;
        }
        try {
            return Integer.parseInt(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            log.warn("[saramin] 전체 건수를 숫자로 읽지 못했다. field={}", field);
            return JobPostingPage.UNKNOWN_TOTAL;
        }
    }

    /**
     * @param total 공급자가 알려준 전체 건수. 모르면 {@link JobPostingPage#UNKNOWN_TOTAL}
     * @param items 번역 전 공고들
     */
    public record ParsedPage(int total, List<SaraminJobPostingRaw> items) {
    }
}
