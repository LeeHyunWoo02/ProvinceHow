package SDD.smash.domain.job.domain.model;

import java.util.List;

/**
 * 채용공고 목록의 한 페이지.
 *
 * @param pageNumber            1부터 시작하는 페이지 번호
 * @param totalCount            공급자가 알려준 전체 건수. 모르면 {@code -1}
 * @param postings              이 페이지에서 <b>집계 가능한</b> 공고들
 * @param unresolvedRegionCount 지역을 우리 시군구 코드로 옮기지 못해 버린 공고 수
 * @param unresolvedJobCount    직종을 우리 중분류 코드로 옮기지 못해 버린 공고 수
 */
public record JobPostingPage(int pageNumber,
                             int totalCount,
                             List<JobPosting> postings,
                             int unresolvedRegionCount,
                             int unresolvedJobCount) {

    public static final int UNKNOWN_TOTAL = -1;

    public JobPostingPage {
        postings = (postings == null) ? List.of() : List.copyOf(postings);
    }

    public static JobPostingPage empty(int pageNumber) {
        return new JobPostingPage(pageNumber, 0, List.of(), 0, 0);
    }

    /** 이 페이지에서 아무 공고도 얻지 못했는가(버려진 것 포함해 원본이 비었는가와는 다르다). */
    public boolean isEmpty() {
        return postings.isEmpty() && unresolvedRegionCount == 0 && unresolvedJobCount == 0;
    }

    /** 공급자가 준 전체 건수 기준으로 다음 페이지가 남았는가. 전체 건수를 모르면 항상 참이다. */
    public boolean hasNext(int pageSize) {
        if (totalCount == UNKNOWN_TOTAL) {
            return true;
        }
        return (long) pageNumber * pageSize < totalCount;
    }
}
