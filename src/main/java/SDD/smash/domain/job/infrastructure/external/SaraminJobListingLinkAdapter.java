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
 * <h2>지역: 역매핑을 거친 사람인 코드만 싣는다</h2>
 * 사람인 {@code loc_cd} 는 우리 {@link SigunguCode}(행정표준 5자리)와 체계가 다르다.
 * 우리 코드를 그대로 실으면 사람인이 <b>HTTP 301 + 본문 0바이트</b>로 튕겨 페이지가 열리지 않는다
 * (2026-08-31 실측: {@code loc_cd=12110} → 301, {@code loc_cd=112080} → 200).
 * 그래서 {@link SaraminLocCodeResolver} 로 사람인 코드를 얻어 그 값만 싣는다.
 *
 * <p><b>역매핑이 없으면 지역 파라미터를 빼고 링크를 만든다.</b> 링크는 사용자를 보내는 표시용이므로,
 * 전국 목록이라도 열리는 편이 301 로 튕겨 아무것도 못 보는 것보다 낫다는 판단이다
 * (호출 예산을 쓰는 API 조회와 달리 "전국을 지역인 척" 내려주는 데이터 왜곡이 없다).
 *
 * <h2>직종: 지금은 붙이지 않는다</h2>
 * 스펙 JSON 의 {@code mapping.jobCodes} 는 21건뿐이라 우리 중분류 114종 중 18종만 덮고,
 * 역방향에 중복이 있다({@code 015} ← 3개, {@code 017} ← 2개). 어느 사람인 코드를 고를지
 * 결정할 근거가 없고, 틀린 {@code job_cd} 를 붙이면 지역 필터까지 함께 깨질 수 있다.
 * 그래서 {@link #linkFor(SigunguCode, JobCode)} 도 지역만 필터한다.
 *
 * <p>되살릴 조건: {@code mapping.jobCodes} 의 <b>역방향 중복이 0</b>이고 우리 중분류를 충분히
 * 덮으면, 지역과 같은 방식으로 역매핑해 {@code jobParam} 을 붙인다.
 * {@code job-param} 설정은 그때를 위해 남겨 둔다.
 *
 * <p>외부 사이트의 쿼리 파라미터 어휘({@code loc_cd}, {@code job_cd})는 이 클래스 밖으로 나가지 않는다.
 */
@Component
@ConditionalOnProperty(name = "apis.job.provider", havingValue = "saramin", matchIfMissing = true)
public class SaraminJobListingLinkAdapter implements JobListingLinkProvider {

    private final SaraminLocCodeResolver locCodeResolver;

    private final String baseUrl;
    private final String path;
    private final String regionParam;

    /** 직종 파라미터명. 현재는 붙이지 않지만 매핑표가 채워지면 쓸 자리다(클래스 주석 참고). */
    @SuppressWarnings("unused")
    private final String jobParam;

    public SaraminJobListingLinkAdapter(
            SaraminLocCodeResolver locCodeResolver,
            @Value("${saramin.listing.base-url:https://www.saramin.co.kr}") String baseUrl,
            @Value("${saramin.listing.path:/zf_user/jobs/list/domestic}") String path,
            @Value("${saramin.listing.region-param:loc_cd}") String regionParam,
            @Value("${saramin.listing.job-param:job_cd}") String jobParam) {
        this.locCodeResolver = locCodeResolver;
        this.baseUrl = baseUrl;
        this.path = path;
        this.regionParam = regionParam;
        this.jobParam = jobParam;
    }

    @Override
    public String linkFor(SigunguCode sigunguCode) {
        return listingUrl(sigunguCode);
    }

    /** 직종은 붙이지 않는다(클래스 주석의 커버리지·역방향 중복 근거). 지역만 필터한다. */
    @Override
    public String linkFor(SigunguCode sigunguCode, JobCode jobCode) {
        return listingUrl(sigunguCode);
    }

    private String listingUrl(SigunguCode sigunguCode) {
        String saraminLocCode = locCodeResolver.resolve(sigunguCode);
        if (saraminLocCode == null) {
            return baseUrl + path;   // 우리 코드를 그대로 실으면 301 로 튕긴다
        }
        return baseUrl + path + "?" + regionParam + "=" + saraminLocCode;
    }
}
