package SDD.smash.domain.job.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 채용공고 카드 목록 캐시 페이로드. redis-conventions §3.1 이 요구하는 전용
 * {@code RedisTemplate<String, JobVacancyListPayload>} 의 값 타입이다.
 * {@code global/config/RedisConfig} 가 이 클래스를 참조해 전용 템플릿 빈을 만든다
 * (support 의 {@code SupportPolicyListPayload} 와 같은 방식).
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class JobVacancyListPayload {

    private List<JobVacancyPayload> vacancies;
}
