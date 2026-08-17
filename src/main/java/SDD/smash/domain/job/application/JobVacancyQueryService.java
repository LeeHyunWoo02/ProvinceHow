package SDD.smash.domain.job.application;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.application.dto.JobVacancyView;
import SDD.smash.domain.job.domain.model.JobVacancy;
import SDD.smash.domain.job.domain.port.JobVacancyCache;
import SDD.smash.domain.job.domain.port.JobVacancyProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 개별 채용공고 목록 조회 유스케이스. {@code recommendation} 이 지역 상세에서 이 Service 를 호출한다.
 *
 * <p>흐름: 캐시 확인 → (미스면) 단일비행 락 획득 → 재확인 → 외부 공급자 호출 → 캐시 적재 → DTO 변환.
 *
 * <p><b>트랜잭션이 없다.</b> DB(JPA)를 건드리지 않고 캐시와 외부 API 만 쓴다.
 *
 * <h2>스탬피드 방지</h2>
 * 콜드 캐시에서 같은 지역 동시 요청이 각자 사람인을 때리지 않도록 {@link KeyedLock}(지역별 in-process
 * 락)으로 단일비행한다. 락 획득에 실패(타임아웃)하면 외부 호출을 늘리지 않고 캐시를 한 번 더 본 뒤
 * 없으면 빈 목록으로 degrade 한다(500회/일 예산 보호). 단일 인스턴스 전제 — 근거는 {@link KeyedLock}.
 *
 * <h2>네거티브 캐싱</h2>
 * 공급자가 <b>실제 조회를 시도해</b> 0건을 준 경우({@code Optional.of(빈 목록)})는 짧은 TTL 로 캐싱해
 * 재호출을 막는다. access-key 미설정·역매핑 부재·호출 실패 등 <b>미시도</b>({@code Optional.empty()})는
 * 캐싱하지 않아, 키를 넣거나 매핑을 채우면 즉시 동작한다.
 */
@Service
@Slf4j
public class JobVacancyQueryService {

    private final JobVacancyProvider jobVacancyProvider;
    private final JobVacancyCache jobVacancyCache;
    private final KeyedLock<SigunguCode> singleFlight = new KeyedLock<>();

    /** 한 지역에서 가져올 카드 수. 사람인 500회/일 제한 때문에 작게 잡는다. */
    private final int listSize;
    /** 단일비행 락 대기 한도. 초과하면 빈 목록으로 degrade. */
    private final Duration lockTimeout;

    public JobVacancyQueryService(JobVacancyProvider jobVacancyProvider,
                                  JobVacancyCache jobVacancyCache,
                                  @Value("${apis.saramin.vacancy.list-size:5}") int listSize,
                                  @Value("${apis.saramin.cache.lock-timeout:PT3S}") Duration lockTimeout) {
        this.jobVacancyProvider = jobVacancyProvider;
        this.jobVacancyCache = jobVacancyCache;
        this.listSize = Math.max(1, listSize);
        this.lockTimeout = lockTimeout;
    }

    public List<JobVacancyView> getVacancies(SigunguCode region) {
        Optional<List<JobVacancy>> cached = jobVacancyCache.find(region);
        if (cached.isPresent()) {
            return toViews(cached.get());
        }

        ReentrantLock lock = singleFlight.forKey(region);
        boolean acquired;
        try {
            acquired = lock.tryLock(lockTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return List.of();
        }
        if (!acquired) {
            // 다른 요청이 채우는 중 → 캐시를 한 번 더 보고, 없으면 외부 호출 없이 빈 목록으로 degrade.
            log.warn("[vacancy] 단일비행 락 대기 초과 region={} - 캐시 재확인 후 degrade", region.value());
            return jobVacancyCache.find(region).map(this::toViews).orElseGet(List::of);
        }
        try {
            Optional<List<JobVacancy>> recheck = jobVacancyCache.find(region);   // 락 안에서 재확인
            if (recheck.isPresent()) {
                return toViews(recheck.get());
            }
            Optional<List<JobVacancy>> fetched = jobVacancyProvider.findVacancies(region, listSize);
            if (fetched.isEmpty()) {
                return List.of();   // 미시도 → 캐싱하지 않는다.
            }
            List<JobVacancy> vacancies = fetched.get();
            jobVacancyCache.put(region, vacancies);   // 정상 or 네거티브(빈 목록) 캐싱
            return toViews(vacancies);
        } finally {
            lock.unlock();
        }
    }

    private List<JobVacancyView> toViews(List<JobVacancy> vacancies) {
        return vacancies.stream().map(JobVacancyView::from).toList();
    }
}
