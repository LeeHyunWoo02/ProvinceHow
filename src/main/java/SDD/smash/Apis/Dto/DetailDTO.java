package SDD.smash.Apis.Dto;

import SDD.smash.Dwelling.Dto.DwellingInfoDTO;
import SDD.smash.Infra.Dto.InfraDetails;
<<<<<<< HEAD
import SDD.smash.Infra.Dto.InfraMajor;
=======
>>>>>>> origin/Backup/main
import SDD.smash.Job.Dto.JobInfoDTO;
import SDD.smash.Support.dto.SupportListDTO;
import lombok.Builder;
import lombok.Getter;
<<<<<<< HEAD
=======
import lombok.Setter;
>>>>>>> origin/Backup/main

import java.util.List;

@Getter
@Builder
public class DetailDTO {

    private String sidoCode;
    private String sidoName;

    private String sigunguCode;
    private String sigunguName;

<<<<<<< HEAD
    private Integer population;

=======
>>>>>>> origin/Backup/main
    // 일자리
    private JobInfoDTO totalJobInfo;
    private JobInfoDTO fitJobInfo;

    //지원사업
    private Integer totalSupportNum;

    private SupportListDTO supportList;

    //주거
    private DwellingInfoDTO dwellingInfo;

    //인프라
    private List<InfraDetails> infraDetails;

<<<<<<< HEAD
    //주 인프라 점수
    private List<InfraMajor> infraMajors;

=======
>>>>>>> origin/Backup/main
}
