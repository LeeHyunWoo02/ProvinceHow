package SDD.smash.Dwelling.Dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DwellingUpsertDTO {

    // 시군구 코드
    private String sigunguCode;     

    // 월세 지표
    private Double monthAvg;     // 평균(만원/월)
    private Integer monthMid;  // 중앙값(만원/월)

    // 전세 지표(보증금)
    private Double jeonseAvg;    // 평균 보증금(만원)
    private Integer jeonseMid; // 중앙값 보증금(만원)

}
