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
 *
 * <p><b>네거티브 캐싱</b>: {@code put} 에 빈 목록을 주면 "0건"을 짧은 TTL 로 캐싱한다(재호출 방지).
 * 그러면 {@code find} 는 그 지역에 대해 <b>비어 있지 않은 {@code Optional}(내용은 빈 목록)</b>을 돌려준다 —
 * 즉 "캐시된 0건 히트"다. 키가 아예 없을 때만 {@link Optional#empty()}(미스)다.
 */
public interface JobVacancyCache {

    /** @return 키가 있으면 캐시된 결과(빈 목록이면 캐시된 0건). 키가 없으면 {@link Optional#empty()}. */
    Optional<List<JobVacancy>> find(SigunguCode region);

    /** 빈 목록을 주면 짧은 TTL 로 네거티브 캐싱, 아니면 정상 TTL 로 캐싱한다. */
    void put(SigunguCode region, List<JobVacancy> vacancies);

    void evictAll();
}
