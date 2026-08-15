package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.domain.port.JobListingLinkProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 워크넷 채용 목록 링크 생성 어댑터. {@code JobListingLinkProvider} 포트 구현이다.
 * As-Is {@code JobService.generateUrl / generateFitUrl} 을 옮긴 것이며 문자열 조립을 그대로 유지했다.
 *
 * <p>외부 사이트의 쿼리 파라미터 어휘({@code region}, {@code occupation}, {@code resultCnt})는
 * 이 클래스 밖으로 나가지 않는다.
 */
@Component
@ConditionalOnProperty(name = "apis.job.provider", havingValue = "worknet")
public class WorknetJobListingLinkAdapter implements JobListingLinkProvider {

    @Value("${worknet.base-url}")
    private String baseUrl;

    @Value("${worknet.path}")
    private String path;

    @Override
    public String linkFor(SigunguCode sigunguCode) {
        return baseUrl + path
                + "?region=" + sigunguCode.value()
                + "&resultCnt=10"
                + "&pageIndex=1";
    }

    @Override
    public String linkFor(SigunguCode sigunguCode, JobCode jobCode) {
        return baseUrl + path
                + "?occupation=" + jobCode.value()
                + "&region=" + sigunguCode.value()
                + "&resultCnt=10"
                + "&pageIndex=1";
    }
}
