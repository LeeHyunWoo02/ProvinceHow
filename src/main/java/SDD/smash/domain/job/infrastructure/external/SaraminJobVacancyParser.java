package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobVacancyRaw;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 사람인 채용정보 응답(JSON)에서 <b>표시용 카드 필드</b>를 뽑는 파서.
 * 집계용 {@link SaraminJobPostingParser}(id·코드만) 와 달리 제목·기업·연봉 등 풍부한 필드를 읽는다.
 *
 * <p>응답 골격 파싱은 {@link SaraminResponseReader} 가 하고, 이 파서는 job 노드를
 * {@link SaraminJobVacancyRaw} 로 옮기는 일만 맡는다. 카드 표시 필드의 경로는 사람인 공식 응답
 * 구조상 안정적이라 이 파서의 상수로 둔다(집계 코드 필드와 달리 매핑 대상이 아니다).
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

    private final SaraminResponseReader responseReader;

    /**
     * @return 표시용 원문 카드 목록. 목록이 없으면 빈 리스트.
     * @throws SaraminApiException 응답이 오류이거나 파싱할 수 없을 때
     */
    public List<SaraminJobVacancyRaw> parse(String body, SaraminApiSpecFile.Response spec) {
        return responseReader.read(body, spec).jobNodes().stream()
                .map(this::toRaw)
                .toList();
    }

    private SaraminJobVacancyRaw toRaw(JsonNode node) {
        return new SaraminJobVacancyRaw(
                SaraminResponseReader.textOf(node, FIELD_ID),
                SaraminResponseReader.textAt(node, PATH_TITLE),
                SaraminResponseReader.textAt(node, PATH_COMPANY),
                SaraminResponseReader.textOf(node, PATH_URL),
                SaraminResponseReader.textAt(node, PATH_REGION_NAME),
                SaraminResponseReader.textAt(node, PATH_JOB_NAME),
                SaraminResponseReader.textAt(node, PATH_SALARY),
                SaraminResponseReader.textAt(node, PATH_EXPERIENCE),
                SaraminResponseReader.textAt(node, PATH_EDUCATION),
                SaraminResponseReader.textAt(node, PATH_EMPLOYMENT),
                SaraminResponseReader.textOf(node, FIELD_ACTIVE),
                SaraminResponseReader.textOf(node, FIELD_POSTING_TS),
                SaraminResponseReader.textOf(node, FIELD_EXPIRATION_TS));
    }
}
