package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobPostingPage;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobPostingRaw;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사람인 채용정보 응답(JSON)에서 <b>집계용 원자료</b>(id·지역/직종 코드)와 전체 건수를 뽑는 파서.
 *
 * <p>응답 골격 파싱은 {@link SaraminResponseReader} 가 하고, 이 파서는 job 노드를
 * {@link SaraminJobPostingRaw} 로 옮기는 일과 전체 건수 해석만 맡는다.
 *
 * <p>응답 구조(핵심):
 * <pre>{@code
 * { "jobs": { "count": 10, "start": 0, "total": "12345",
 *             "job": [ { "id": "1", "position": { "location": {"code":"101000"}, ... } } ] } }
 * }</pre>
 * {@code total} 은 <b>문자열</b>로 온다. {@code job} 은 한 건이면 배열이 아니라 단일 객체로 올 수도 있다.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SaraminJobPostingParser {

    private final SaraminResponseReader responseReader;

    /**
     * @return 원문 그대로의 공고 목록과 전체 건수
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public ParsedPage parse(String body, SaraminApiSpecFile.Response spec) {
        SaraminResponseReader.JobsPayload payload = responseReader.read(body, spec);
        int total = intValue(payload.jobsRoot(), spec.totalField());
        List<SaraminJobPostingRaw> items = payload.jobNodes().stream()
                .map(node -> toRaw(node, spec))
                .toList();
        return new ParsedPage(total, items);
    }

    private SaraminJobPostingRaw toRaw(JsonNode node, SaraminApiSpecFile.Response spec) {
        String id = SaraminResponseReader.textOf(node, spec.postingIdField());
        String region = SaraminResponseReader.textAt(node, spec.regionCodePath());
        String job = SaraminResponseReader.textAt(node, spec.jobCodePath());
        return new SaraminJobPostingRaw(id, region, job);
    }

    private int intValue(JsonNode node, String field) {
        String raw = SaraminResponseReader.textOf(node, field);
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
