package SDD.smash.legacy.infra.Dto;

import SDD.smash.legacy.infra.Entity.Major;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.math.BigDecimal;

@AllArgsConstructor
@Getter
public class InfraDetails {
    Major major;
    String name; //업종 상세명
    Integer num;
    BigDecimal ratio;
}
