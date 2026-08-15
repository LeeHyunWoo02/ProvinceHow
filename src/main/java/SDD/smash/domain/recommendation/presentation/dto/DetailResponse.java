package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.DwellingInfoSummary;
import SDD.smash.domain.recommendation.application.dto.IndustryDetailItem;
import SDD.smash.domain.recommendation.application.dto.JobInfoSummary;
import SDD.smash.domain.recommendation.application.dto.MajorInfraSummaryItem;
import SDD.smash.domain.recommendation.application.dto.SupportPolicyListSummary;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 지역 상세 조회 응답. As-Is {@code DetailResponseDTO} 자리를 대신한다.
 *
 * <p>{@code OpenAI.Converter.AiConverter}/{@code DetailAiSummaryService} 가 이 클래스를
 * {@code .builder()...build()} 로 만든다. 그 호출부를 바꾸지 않으므로 Lombok 클래스로
 * 유지한다({@code recommendation.application.dto.RegionRecommendation} 과 같은 이유).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailResponse {

    private String sidoCode;
    private String sidoName;
    private String sigunguCode;
    private String sigunguName;

    private Integer population;

    private JobInfoSummary totalJobInfo;
    private JobInfoSummary fitJobInfo;

    private List<JobVacancyEntry> jobVacancies;

    private Integer totalSupportNum;
    private SupportPolicyListSummary supportList;

    private DwellingInfoSummary dwellingInfo;

    private List<IndustryDetailItem> infraDetails;
    private List<MajorInfraSummaryItem> infraMajors;

    private String aiSummary;
}
