package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.domain.recommendation.application.dto.RegionPick;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;
import SDD.smash.domain.recommendation.presentation.dto.AiPickEntry;
import SDD.smash.domain.recommendation.presentation.dto.DetailResponse;
import SDD.smash.domain.recommendation.presentation.dto.JobVacancyEntry;
import SDD.smash.domain.recommendation.presentation.dto.RecommendAggregateResponse;
import SDD.smash.domain.recommendation.presentation.dto.RegionJobProfileEntry;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 유스케이스 결과 + AI 결과를 HTTP 응답 DTO 로 조립한다.
 *
 * <p><b>왜 presentation 에 있는가</b> — 이 클래스가 만드는 것이
 * {@code presentation/dto} 의 {@code DetailResponse}/{@code RecommendAggregateResponse} 이므로
 * 응답 계약의 소유자인 이 계층에 있어야 한다(architecture-conventions §4 표).
 * 이전에는 {@code infrastructure/external} 에 있어서
 * <b>infrastructure → presentation</b> 역방향 의존을 만들고 있었다.
 *
 * <p>AI 결과는 {@code application/port/out} 의 포트가 돌려준 값
 * ({@code List<RegionPick>} / 요약 문자열)로만 받는다. 외부 LLM 응답 타입
 * ({@code infrastructure/external/dto/AiRecommendDTO})을 더 이상 알지 않는다.
 */
public class AiConverter {

    public static DetailResponse toResponseDTO(RegionDetailInfo dto, @Nullable String summarizeContent){
        return DetailResponse.builder()
                .sidoCode(dto.getSidoCode())
                .sidoName(dto.getSidoName())
                .sigunguCode(dto.getSigunguCode())
                .sigunguName(dto.getSigunguName())
                .population(dto.getPopulation())
                .totalJobInfo(dto.getTotalJobInfo())
                .fitJobInfo(dto.getFitJobInfo())
                .jobVacancies(dto.getJobVacancies() == null ? List.of()
                        : dto.getJobVacancies().stream().map(JobVacancyEntry::from).toList())
                .regionJobProfile(RegionJobProfileEntry.from(dto.getRegionJobProfile()))
                .totalSupportNum(dto.getTotalSupportNum())
                .supportList(dto.getSupportList())
                .dwellingInfo(dto.getDwellingInfo())
                .infraDetails(dto.getInfraDetails())
                .infraMajors(dto.getInfraMajors())
                .aiSummary(summarizeContent)
                .build();
    }

    /**
     * @param picks AI 픽. {@code null} 또는 빈 목록이면 {@code aiPick} 이 빈 배열이 된다.
     *              As-Is 는 {@code AiRecommendDTO} 가 null 이거나 그 안의
     *              {@code recommendations} 가 null 일 때 {@code List.of()} 를 넣었는데,
     *              그 두 경우가 이제 "빈 목록"으로 합쳐진 것이다 — 응답 JSON 은 동일하다.
     */
    public static RecommendAggregateResponse toResponseList(List<RegionRecommendation> recommendDTOList,
                                                            @Nullable List<RegionPick> picks){
        List<RegionRecommendation> items = recommendDTOList.stream()
                .map(dto -> RegionRecommendation.builder()
                        .sidoCode(dto.getSidoCode())
                        .sidoName(dto.getSidoName())
                        .sigunguCode(dto.getSigunguCode())
                        .sigunguName(dto.getSigunguName())
                        .score(dto.getScore())
                        .totalJobInfo(dto.getTotalJobInfo())
                        .fitJobInfo(dto.getFitJobInfo())
                        .totalSupportNum(dto.getTotalSupportNum())
                        .fitSupportNum(dto.getFitSupportNum())
                        .dwellingSimpleInfo(dto.getDwellingSimpleInfo())
                        .infraMajors(dto.getInfraMajors())
                        .build())
                .toList();

        List<AiPickEntry> aiPick = (picks == null)
                ? List.of()
                : picks.stream()
                .map(p -> AiPickEntry.builder()
                        .aiPickSigunguCode(p.sigunguCode())
                        .aiPickReason(p.reason())
                        .build())
                .toList();

        return RecommendAggregateResponse.builder()
                .items(items)
                .aiPick(aiPick)
                .build();
    }
}
