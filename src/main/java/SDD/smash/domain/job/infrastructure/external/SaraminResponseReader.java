package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 사람인 채용정보 응답(JSON)의 공통 골격을 읽는다. 세 파서(집계/카드/프로필)가 공유한다.
 *
 * <p>본문 검증 → readTree → 오류 판정 → rootField/listField 추출 → 배열/단일 분기까지 한다.
 * 각 파서는 얻은 job 노드를 자기 Raw 로 변환하는 일만 맡는다. JSON 탐색 헬퍼도 여기 static 으로 둔다.
 *
 * <p><b>응답 전체를 로그로 찍지 않는다.</b> Jackson 2 를 쓴다.
 */
@Component
@RequiredArgsConstructor
public class SaraminResponseReader {

    private final ObjectMapper objectMapper;

    /**
     * @return jobs 루트 노드와 job 원소 목록(한 건이면 단일 객체도 목록화)
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public JobsPayload read(String body, SaraminApiSpecFile.Response spec) {
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
            throw new SaraminApiException("[saramin] API 오류 응답 message=" + textAt(root, spec.errorMessageField()));
        }

        JsonNode jobsRoot = root.get(spec.rootField());
        if (jobsRoot == null || jobsRoot.isMissingNode() || jobsRoot.isNull()) {
            throw new SaraminApiException(
                    "[saramin] 응답에 '" + spec.rootField() + "' 오브젝트가 없다. 스펙 rootField 를 확인하라.");
        }

        List<JsonNode> jobNodes = new ArrayList<>();
        JsonNode list = jobsRoot.get(spec.listField());
        if (list != null && !list.isMissingNode() && !list.isNull()) {
            if (list.isArray()) {
                list.forEach(jobNodes::add);
            } else {
                jobNodes.add(list);
            }
        }
        return new JobsPayload(jobsRoot, List.copyOf(jobNodes));
    }

    /** 점(.)으로 표현된 중첩 경로를 따라 내려가 노드를 얻는다. 없으면 {@code null}. */
    public static JsonNode at(JsonNode base, String dotPath) {
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

    public static String textAt(JsonNode base, String dotPath) {
        return asText(at(base, dotPath));
    }

    public static String textOf(JsonNode node, String field) {
        return asText((node == null) ? null : node.get(field));
    }

    public static String asText(JsonNode value) {
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asText("").trim();
    }

    /**
     * @param jobsRoot jobs 루트 노드(전체 건수 등 부가 필드가 여기 있다)
     * @param jobNodes job 원소 노드 목록
     */
    public record JobsPayload(JsonNode jobsRoot, List<JsonNode> jobNodes) {
    }
}
