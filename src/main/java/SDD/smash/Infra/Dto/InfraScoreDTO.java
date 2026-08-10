package SDD.smash.Infra.Dto;

import lombok.Getter;

@Getter
public class InfraScoreDTO {
    private String sigungu_code;
    private Integer score;
    public InfraScoreDTO(String sigungu_code, Integer score) {
        this.sigungu_code = sigungu_code;
        this.score = score;
    }
}
