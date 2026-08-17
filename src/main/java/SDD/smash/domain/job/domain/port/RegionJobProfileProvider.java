package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobPostingSample;

import java.util.List;

/**
 * 지역 채용 프로필의 <b>표본</b> 공급자. out-port 다.
 *
 * <p>공급자는 표본만 가져온다 — 집계(중앙값·비율·업종 Top)는 도메인 정책이 한다.
 * 지역을 공급자 코드로 옮길 수 없거나 설정이 없으면 <b>빈 표본</b>을 돌려준다.
 */
public interface RegionJobProfileProvider {

    /**
     * @param region     조회할 시군구
     * @param sampleSize 가져올 최대 표본 수(공급자 상한을 넘으면 어댑터가 상한으로 맞춘다)
     * @return 표본 목록. 설정이 없거나 지역을 옮길 수 없으면 빈 목록
     */
    List<JobPostingSample> sample(SigunguCode region, int sampleSize);
}
