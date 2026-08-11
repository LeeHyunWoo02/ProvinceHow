package SDD.smash.Infra.Dto;

import SDD.smash.Infra.Entity.Major;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class InfraMajor {
    private Major major;
    private Long num;
    private Double score;
}
