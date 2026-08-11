package SDD.smash.legacy.infra.Dto;

import SDD.smash.legacy.infra.Entity.Major;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InfraMajor {
    private Major major;
    private Long num;
    private Double score;
}
