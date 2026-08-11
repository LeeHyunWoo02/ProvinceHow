package SDD.smash.legacy.infra.Dto;

import SDD.smash.legacy.infra.Entity.Major;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SigunguMajorAvgDTO {

    private String sigunguCode;
    private Major major;
    private Double score;
}
