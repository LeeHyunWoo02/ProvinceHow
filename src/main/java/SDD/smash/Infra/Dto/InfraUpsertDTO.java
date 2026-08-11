package SDD.smash.Infra.Dto;


import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;

@Getter
@Builder
public class InfraUpsertDTO {
    // 시군구 코드
    private String sigunguCode;
    // 직종 코드
    private String industryCode;
    // 직종 개수
    private String count;
    private BigDecimal ratio;
    private BigDecimal score;


}
