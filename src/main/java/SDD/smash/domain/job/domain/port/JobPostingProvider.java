package SDD.smash.domain.job.domain.port;

import SDD.smash.domain.job.domain.model.JobPostingPage;

/**
 * 외부 채용공고 공급자. out-port 다.
 *
 * <p>도메인은 "채용공고를 페이지 단위로 받아온다"만 안다.
 * 공급자의 URL·파라미터명·응답 필드명·코드 체계는 구현 어댑터 안에서 끝난다.
 *
 * <p>구현체는 <b>절대 페이지 전체를 한 번에 모아 돌려주지 않는다.</b>
 * 호출자가 한 페이지씩 당겨 쓰는 것을 전제로 한다.
 */
public interface JobPostingProvider {

    /**
     * @param pageNumber 1부터 시작하는 페이지 번호
     * @param pageSize   한 페이지 건수
     * @return 해당 페이지. 더 없으면 비어 있는 페이지
     */
    JobPostingPage fetchPage(int pageNumber, int pageSize);

    /** 공급자를 쓸 수 있는 상태인가(인증키 등 필수 설정이 채워졌는가). */
    boolean isConfigured();

    /** 한 번에 요청할 수 있는 최대 건수. */
    int maxPageSize();
}
