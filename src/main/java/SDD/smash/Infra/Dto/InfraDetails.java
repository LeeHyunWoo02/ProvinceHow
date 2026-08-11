package SDD.smash.Infra.Dto;

import SDD.smash.Infra.Entity.Major;
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
