package SDD.smash.domain.job.infrastructure.external.dto;

import java.util.List;

/**
 * 워크넷 응답의 {@code <wanted>} 한 건을 <b>번역 전 원문 그대로</b> 담은 기술 DTO.
 *
 * <p>이 타입은 {@code infrastructure/external} 밖으로 나가지 않는다.
 * 어댑터가 {@code JobPosting}(도메인)으로 옮긴 뒤 버린다.
 *
 * @param postingId    구인인증번호({@code wantedAuthNo})
 * @param regionCodes  응답에서 읽은 지역코드들. 워크넷 코드 체계 그대로다
 * @param jobCodes     응답에서 읽은 직종코드들. 워크넷 코드 체계 그대로다
 */
public record WorknetJobPostingRaw(String postingId, List<String> regionCodes, List<String> jobCodes) {

    public WorknetJobPostingRaw {
        regionCodes = (regionCodes == null) ? List.of() : List.copyOf(regionCodes);
        jobCodes = (jobCodes == null) ? List.of() : List.copyOf(jobCodes);
    }
}
