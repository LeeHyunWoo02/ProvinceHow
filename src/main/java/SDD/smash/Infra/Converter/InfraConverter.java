package SDD.smash.Infra.Converter;

import SDD.smash.Infra.Dto.IndustryDTO;
import SDD.smash.Infra.Entity.Industry;
import SDD.smash.Infra.Entity.Major;

import static SDD.smash.Util.BatchTextUtil.normalize;

public class InfraConverter {
    public static Industry industryToEntity(IndustryDTO dto){
        return Industry.builder()
                .code(normalize(dto.getCode()))
                .name(normalize(dto.getName()))
                .major(Major.valueOf(normalize(dto.getMajor())))
                .build();
    }
}
