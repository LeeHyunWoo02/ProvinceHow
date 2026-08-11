package SDD.smash.Infra.Dto;


import lombok.Getter;

import java.math.BigDecimal;

@Getter
public class InfraDTO {
    // 시군구 코드
    private String sigungu_code;
    // 직종 코드
    private String industry_code;
    // 직종 개수
    private String count;
    private BigDecimal ratio;
    private BigDecimal score;


    public InfraDTO(String sigungu_code, String industry_code, String count,
                    BigDecimal ratio, BigDecimal score) {
        this.sigungu_code = sigungu_code;
        this.industry_code = industry_code;
        this.count = count;
        this.ratio = ratio;
        this.score = score;
    }

}
