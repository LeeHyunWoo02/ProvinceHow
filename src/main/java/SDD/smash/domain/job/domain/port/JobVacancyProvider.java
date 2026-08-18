package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobVacancy;

import java.util.List;
import java.util.Optional;

/**
 * 개별 채용공고 목록 공급자. out-port 다. 집계용 {@link JobPostingProvider} 와 분리한다 —
 * 집계는 전 지역을 페이지로 훑고, 이 포트는 <b>한 지역의 카드 몇 개</b>만 가져온다.
 *
 * <p>도메인은 "이 지역의 채용공고 목록을 최대 몇 건 받아온다"만 안다. 공급자의 URL·
 * 파라미터명·지역코드 체계는 구현 어댑터 안에서 끝난다.
 */
public interface JobVacancyProvider {

    /**
     * @param region 조회할 시군구
     * @param size   가져올 최대 건수(공급자 상한을 넘으면 어댑터가 상한으로 맞춘다)
     * @return <b>실제 조회를 시도한 경우</b>의 결과(0건이면 빈 목록). 설정 미비(access-key 없음)·
     *         지역 역매핑 부재·호출 실패로 <b>조회 자체를 하지 못하면</b> {@link Optional#empty()}.
     *         이 구분으로 유스케이스가 "실제 0건"만 네거티브 캐싱하고 "미시도"는 캐싱하지 않는다.
     */
    Optional<List<JobVacancy>> findVacancies(SigunguCode region, int size);
}
