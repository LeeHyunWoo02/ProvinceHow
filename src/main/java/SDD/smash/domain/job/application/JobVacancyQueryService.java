package SDD.smash.domain.job.application;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.application.dto.JobVacancyView;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.domain.port.JobVacancyCache;
import SDD.smash.domain.job.domain.port.JobVacancyProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 개별 채용공고 목록 조회 유스케이스. {@code recommendation} 이 지역 상세에서 이 Service 를 호출한다.
 *
 * <p>흐름: 캐시 확인 → (미스면) 외부 공급자 호출 → 캐시 적재 → 표시 DTO 변환.
 *
 * <p><b>트랜잭션이 없다.</b> DB(JPA)를 건드리지 않고 캐시와 외부 API 만 쓴다 —
 * 트랜잭션 안에서 외부 API 를 부르지 않는다는 규칙(persistence-conventions §6.3)과도 맞는다.
 * 사람인 access-key 가 없거나 지역 역매핑이 비어 있으면 공급자 어댑터가 빈 목록을 돌려준다.
 */
@Service
public class JobVacancyQueryService {

    private final JobVacancyProvider jobVacancyProvider;
    private final JobVacancyCache jobVacancyCache;

    /** 한 지역에서 가져올 카드 수. 사람인 500회/일 제한 때문에 작게 잡는다. */
    private final int listSize;

    public JobVacancyQueryService(JobVacancyProvider jobVacancyProvider,
                                  JobVacancyCache jobVacancyCache,
                                  @Value("${apis.saramin.vacancy.list-size:5}") int listSize) {
        this.jobVacancyProvider = jobVacancyProvider;
        this.jobVacancyCache = jobVacancyCache;
        this.listSize = Math.max(1, listSize);
    }

    public List<JobVacancyView> getVacancies(SigunguCode region) {
        Optional<List<JobVacancy>> cached = jobVacancyCache.find(region);
        if (cached.isPresent()) {
            return toViews(cached.get());
        }

        List<JobVacancy> vacancies = jobVacancyProvider.findVacancies(region, listSize);
        if (!vacancies.isEmpty()) {
            jobVacancyCache.put(region, vacancies);
        }
        return toViews(vacancies);
    }

    private List<JobVacancyView> toViews(List<JobVacancy> vacancies) {
        return vacancies.stream().map(JobVacancyView::from).toList();
    }
}
