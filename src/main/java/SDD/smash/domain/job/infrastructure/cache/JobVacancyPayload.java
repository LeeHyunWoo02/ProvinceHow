package SDD.smash.domain.job.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 채용공고 카드 캐시 페이로드(한 건). Jackson 역직렬화용이라 기본 생성자 + setter 를 갖는다.
 * {@code infrastructure/cache} 밖으로 나가지 않는다.
 *
 * <p>날짜는 {@code java.time} 대신 ISO-8601 문자열로 저장한다 — 전용 템플릿의 ObjectMapper 에
 * JavaTimeModule 을 걸지 않아도 되고, redis-cli 로도 그대로 읽힌다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobVacancyPayload {

    private String postingId;
    private String title;
    private String companyName;
    private String detailUrl;
    private String regionName;
    private String jobName;
    private String salaryText;
    private String experienceText;
    private String educationText;
    private String employmentType;
    private boolean active;
    private String postingDate;
    private String expirationDate;
}
