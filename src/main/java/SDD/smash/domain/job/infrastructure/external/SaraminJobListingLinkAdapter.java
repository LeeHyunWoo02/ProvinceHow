package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.port.JobListingLinkProvider;
import SDD.smash.global.domain.model.SigunguCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 사람인 채용 목록 링크 생성 어댑터. {@link JobListingLinkProvider} 포트 구현이다.
 *
 * <p>워크넷 {@code WorknetJobListingLinkAdapter} 를 대체하는 활성 구현이다.
 * {@code apis.job.provider=saramin}(기본값)일 때만 빈으로 등록되어 포트 충돌을 막는다.
 *
 * <h2>⚠️ 어떤 코드로 링크를 만드는가</h2>
 * 이 어댑터는 우리 코드({@link SigunguCode} 5자리 / {@link JobCode})를 사람인 검색 URL 의
 * {@code loc_cd}/{@code job_cd} 파라미터에 <b>그대로</b> 실어 보낸다.
 * <b>사람인 loc_cd/job_cd 는 우리 코드와 체계가 다르므로, 우리 코드→사람인 코드 역매핑표가
 * 구축되기 전에는 이 링크가 사람인에서 지역/직종으로 정확히 필터링되지 않는다.</b>
 * 그래도 링크 자체는 항상 생성된다(사용자는 검색 결과 페이지로는 이동한다).
 *
 * <p>역매핑표가 준비되면 이 어댑터에서 우리 코드를 사람인 코드로 바꾼 뒤 URL 을 조립하도록 확장한다.
 * base-url/path/파라미터명은 설정으로 바꿀 수 있게 열어 두었다.
 *
 * <p>외부 사이트의 쿼리 파라미터 어휘({@code loc_cd}, {@code job_cd})는 이 클래스 밖으로 나가지 않는다.
 */
@Component
@ConditionalOnProperty(name = "apis.job.provider", havingValue = "saramin", matchIfMissing = true)
public class SaraminJobListingLinkAdapter implements JobListingLinkProvider {

    @Value("${saramin.listing.base-url:https://www.saramin.co.kr}")
    private String baseUrl;

    @Value("${saramin.listing.path:/zf_user/jobs/list/domestic}")
    private String path;

    @Value("${saramin.listing.region-param:loc_cd}")
    private String regionParam;

    @Value("${saramin.listing.job-param:job_cd}")
    private String jobParam;

    @Override
    public String linkFor(SigunguCode sigunguCode) {
        return baseUrl + path + "?" + regionParam + "=" + sigunguCode.value();
    }

    @Override
    public String linkFor(SigunguCode sigunguCode, JobCode jobCode) {
        return baseUrl + path
                + "?" + regionParam + "=" + sigunguCode.value()
                + "&" + jobParam + "=" + jobCode.value();
    }
}
