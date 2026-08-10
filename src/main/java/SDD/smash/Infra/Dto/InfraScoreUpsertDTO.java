package SDD.smash.Infra.Dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
@Builder
public class InfraScoreUpsertDTO {
    private String sigunguCode;
    private Integer score;

}
