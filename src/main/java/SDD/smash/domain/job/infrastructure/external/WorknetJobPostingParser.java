package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetJobPostingRaw;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 워크넷 채용목록 응답(XML) 파서.
 *
 * <p>응답 루트는 {@code <wantedRoot>} 이고, {@code XmlMapper} 는 루트 요소명을 벗겨내므로
 * 파싱 결과의 최상위가 곧 {@code wantedRoot} 의 자식들이다.
 *
 * <pre>{@code
 * <wantedRoot>
 *   <total>1419</total>
 *   <wanted><wantedAuthNo>KJAU...</wantedAuthNo>...</wanted>
 *   <wanted>...</wanted>
 * </wantedRoot>
 * }</pre>
 *
 * <p>오류일 때는 목록 대신 {@code <message>}/{@code <messageCd>} 만 온다(실측 확인).
 * <pre>{@code <wantedRoot><message>유효하지 않은 인증키 입니다.</message><messageCd>002</messageCd></wantedRoot>}</pre>
 *
 * <p><b>응답 전체를 로그로 찍지 않는다.</b> 기업명·주소가 들어 있는 데다 양이 크다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorknetJobPostingParser {

    private final XmlMapper xmlMapper;

    /**
     * @return 원문 그대로의 공고 목록과 전체 건수
     * @throws WorknetApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public ParsedPage parse(String body, WorknetApiSpecFile.Response spec) {
        if (body == null || body.isBlank()) {
            throw new WorknetApiException("[worknet] 응답 본문이 비어 있다.");
        }

        JsonNode root;
        try {
            root = xmlMapper.readTree(body.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new WorknetApiException("[worknet] 응답 XML 파싱 실패", e);
        }
        if (root == null || root.isMissingNode()) {
            throw new WorknetApiException("[worknet] 응답 XML 이 비어 있다.");
        }

        String messageCode = text(root, spec.messageCodeElement());
        String message = text(root, spec.messageElement());
        if (messageCode != null && !messageCode.isEmpty()) {
            throw new WorknetApiException(
                    "[worknet] API 오류 응답 messageCd=" + messageCode + ", message=" + message,
                    messageCode, null);
        }

        int total = intValue(root, spec.totalElement());

        JsonNode list = root.get(spec.listElement());
        List<WorknetJobPostingRaw> items = new ArrayList<>();
        if (list != null && !list.isMissingNode()) {
            if (list.isArray()) {
                list.forEach(node -> items.add(toRaw(node, spec)));
            } else {
                items.add(toRaw(list, spec));
            }
        }
        return new ParsedPage(total, List.copyOf(items));
    }

    private WorknetJobPostingRaw toRaw(JsonNode node, WorknetApiSpecFile.Response spec) {
        String id = text(node, spec.postingIdField());
        List<String> regions = codes(node, spec.regionFields(), spec.multiValueDelimiters());
        List<String> jobs = codes(node, spec.jobCodeFields(), spec.multiValueDelimiters());
        return new WorknetJobPostingRaw(id, regions, jobs);
    }

    /**
     * 후보 필드명을 앞에서부터 훑어 <b>처음으로 값이 있는 필드</b>를 쓴다.
     * 필드명이 미확인이라 여러 후보를 두고 실제 응답이 알려주게 하는 구조다.
     */
    private List<String> codes(JsonNode node, List<String> fields, List<String> delimiters) {
        for (String field : fields) {
            String value = text(node, field);
            if (value != null && !value.isEmpty()) {
                return split(value, delimiters);
            }
        }
        return List.of();
    }

    private List<String> split(String value, List<String> delimiters) {
        Set<String> result = new LinkedHashSet<>();
        result.add(value);
        for (String delimiter : delimiters) {
            if (delimiter == null || delimiter.isEmpty() || !value.contains(delimiter)) {
                continue;
            }
            result.clear();
            for (String part : value.split(java.util.regex.Pattern.quote(delimiter))) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    result.add(trimmed);
                }
            }
            break;
        }
        return List.copyOf(result);
    }

    private String text(JsonNode node, String field) {
        JsonNode value = (node == null) ? null : node.get(field);
        if (value == null || value.isNull() || value.isMissingNode()) {
            return null;
        }
        return value.asText("").trim();
    }

    private int intValue(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null || raw.isEmpty()) {
            return JobPostingPage.UNKNOWN_TOTAL;
        }
        try {
            return Integer.parseInt(raw.replace(",", ""));
        } catch (NumberFormatException e) {
            log.warn("[worknet] 전체 건수를 숫자로 읽지 못했다. field={}", field);
            return JobPostingPage.UNKNOWN_TOTAL;
        }
    }

    /**
     * @param total 공급자가 알려준 전체 건수. 모르면 {@link JobPostingPage#UNKNOWN_TOTAL}
     * @param items 번역 전 공고들
     */
    public record ParsedPage(int total, List<WorknetJobPostingRaw> items) {
    }
}
