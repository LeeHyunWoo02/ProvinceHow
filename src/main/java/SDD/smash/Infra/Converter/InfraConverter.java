package SDD.smash.Infra.Converter;

<<<<<<< HEAD
import SDD.smash.Infra.Dto.IndustryDTO;
import SDD.smash.Infra.Entity.Industry;
=======
import SDD.smash.Address.Entity.Sigungu;
import SDD.smash.Infra.Dto.IndustryDTO;
import SDD.smash.Infra.Dto.InfraDTO;
import SDD.smash.Infra.Dto.InfraScoreDTO;
import SDD.smash.Infra.Entity.Industry;
import SDD.smash.Infra.Entity.Infra;
import SDD.smash.Infra.Entity.InfraScore;
>>>>>>> origin/Backup/main
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
<<<<<<< HEAD
=======
    public static Infra infraToEntity(InfraDTO dto, Sigungu sigungu, Industry industry){
        return Infra.builder()
                .count(Integer.parseInt(normalize(dto.getCount())))
                .sigungu(sigungu)
                .industry(industry)
                .ratio(dto.getRatio())
                .build();
    }
    public static InfraScore infraScoreToEntity(InfraScoreDTO dto, Sigungu sigungu){
        return InfraScore.builder()
                .sigungu(sigungu)
                .score(dto.getScore())
                .build();
    }
>>>>>>> origin/Backup/main
}
