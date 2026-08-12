package SDD.smash.domain.job.domain.port;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobCode;

/**
 * 외부 채용 목록 링크 공급 out-port.
 *
 * <p>어느 사이트의 어떤 쿼리 파라미터인지는 어댑터가 안다.
 * 도메인은 "이 지역(과 직종)의 채용 목록을 볼 수 있는 주소가 있다"만 안다.
 */
public interface JobListingLinkProvider {

    String linkFor(SigunguCode sigunguCode);

    String linkFor(SigunguCode sigunguCode, JobCode jobCode);
}
