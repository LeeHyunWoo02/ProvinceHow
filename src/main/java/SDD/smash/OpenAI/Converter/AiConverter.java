package SDD.smash.OpenAI.Converter;

import SDD.smash.OpenAI.Dto.AiRecommendDTO;
import SDD.smash.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.recommendation.application.dto.RegionRecommendation;
import SDD.smash.recommendation.presentation.dto.AiPickEntry;
import SDD.smash.recommendation.presentation.dto.DetailResponse;
import SDD.smash.recommendation.presentation.dto.RecommendAggregateResponse;
import org.springframework.lang.Nullable;

import java.util.List;

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
                .totalSupportNum(dto.getTotalSupportNum())
                .supportList(dto.getSupportList())
                .dwellingInfo(dto.getDwellingInfo())
                .infraDetails(dto.getInfraDetails())
                .infraMajors(dto.getInfraMajors())
                .aiSummary(summarizeContent)
                .build();
    }

    public static RecommendAggregateResponse toResponseList(List<RegionRecommendation> recommendDTOList,
                                                            @Nullable AiRecommendDTO aiRecommendDTO){
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

        List<AiPickEntry> aiPick = (aiRecommendDTO == null || aiRecommendDTO.getRecommendations() == null)
                ? List.of()
                : aiRecommendDTO.getRecommendations().stream()
                .map(p -> AiPickEntry.builder()
                        .aiPickSigunguCode(p.getSigunguCode())
                        .aiPickReason(p.getReason())
                        .build())
                .toList();

        return RecommendAggregateResponse.builder()
                .items(items)
                .aiPick(aiPick)
                .build();
    }
}
