package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobVacancy;

import java.util.List;
import java.util.Optional;

/**
 * 지역별 채용공고 목록 캐시 out-port.
 *
 * <p>파생 캐시다. 없으면 다시 외부 공급자를 부르면 되므로 캐시 실패가 기능 실패가 되어선 안 된다.
 * 사람인은 1일 500회 호출 제한이 있어, 사용자 요청마다 직접 부르지 않고 이 캐시로 흡수한다.
 *
 * <p>원본이 외부 공급자(사람인)라 RDB 배치처럼 무효화할 시점이 없다 — TTL 로만 신선도를 유지한다.
 * {@link #evictAll()} 은 포트 규약을 맞추기 위한 것이고 현재 호출부는 없다.
 */
public interface JobVacancyCache {

    Optional<List<JobVacancy>> find(SigunguCode region);

    void put(SigunguCode region, List<JobVacancy> vacancies);

    void evictAll();
}
