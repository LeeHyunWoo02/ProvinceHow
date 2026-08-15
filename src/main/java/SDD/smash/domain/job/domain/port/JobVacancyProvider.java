package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobVacancy;

import java.util.List;

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
     * @return 공고 목록. 설정이 없거나 지역을 공급자 코드로 옮길 수 없으면 <b>빈 목록</b>
     */
    List<JobVacancy> findVacancies(SigunguCode region, int size);
}
