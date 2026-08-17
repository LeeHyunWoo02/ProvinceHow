package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobPostingSample;

import java.util.List;
import java.util.Optional;

/**
 * 지역 채용 프로필의 <b>표본</b> 공급자. out-port 다.
 *
 * <p>공급자는 표본만 가져온다 — 집계(중앙값·비율·업종 Top)는 도메인 정책이 한다.
 */
public interface RegionJobProfileProvider {

    /**
     * @param region     조회할 시군구
     * @param sampleSize 가져올 최대 표본 수(공급자 상한을 넘으면 어댑터가 상한으로 맞춘다)
     * @return <b>실제 조회를 시도한 경우</b>의 표본(0건이면 빈 목록). 설정 미비·역매핑 부재·호출
     *         실패로 <b>조회 자체를 하지 못하면</b> {@link Optional#empty()}(유스케이스가 네거티브
     *         캐싱 여부를 이 구분으로 정한다).
     */
    Optional<List<JobPostingSample>> sample(SigunguCode region, int sampleSize);
}
