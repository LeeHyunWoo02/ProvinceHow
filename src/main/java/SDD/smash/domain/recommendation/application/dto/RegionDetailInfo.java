package SDD.smash.domain.recommendation.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 지역 상세 조회 결과. As-Is {@code DetailDTO} 자리를 대신한다.
 *
 * <p>{@code RegionRecommendation} 과 같은 이유로 Lombok {@code @Getter @Builder} 클래스다.
 * {@code OpenAI.Converter.AiConverter.toResponseDTO}/{@code DetailAiSummaryService} 가
 * 이 타입의 자바빈 getter를 그대로 호출한다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionDetailInfo {

    private String sidoCode;
    private String sidoName;
    private String sigunguCode;
    private String sigunguName;

    private Integer population;

    private JobInfoSummary totalJobInfo;
    private JobInfoSummary fitJobInfo;

    /** 추천 지역의 실제 채용공고 카드 목록(job 컨텍스트가 사람인에서 가져온 것을 재포장). */
    private List<JobVacancyItem> jobVacancies;

    private Integer totalSupportNum;
    private SupportPolicyListSummary supportList;

    private DwellingInfoSummary dwellingInfo;

    private List<IndustryDetailItem> infraDetails;
    private List<MajorInfraSummaryItem> infraMajors;
}
