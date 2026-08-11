package SDD.smash.Apis.Dto;

import SDD.smash.legacy.dwelling.Dto.DwellingInfoDTO;
import SDD.smash.legacy.infra.Dto.InfraDetails;
import SDD.smash.legacy.infra.Dto.InfraMajor;
import SDD.smash.legacy.job.Dto.JobInfoDTO;
import SDD.smash.legacy.support.dto.SupportListDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class DetailDTO {

    private String sidoCode;
    private String sidoName;

    private String sigunguCode;
    private String sigunguName;

    private Integer population;

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

    //주 인프라 점수
    private List<InfraMajor> infraMajors;

}
