package SDD.smash.Apis.Dto;

import SDD.smash.legacy.dwelling.Dto.DwellingSimpleInfoDTO;
import SDD.smash.Infra.Dto.InfraMajor;
import SDD.smash.Job.Dto.JobInfoDTO;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class RecommendDTO {

    private String sidoCode;
    private String sidoName;

    private String sigunguCode;
    private String sigunguName;

    private Integer score;

    // 일자리
    private JobInfoDTO totalJobInfo;
    private JobInfoDTO fitJobInfo;

    //지원사업
    private Integer totalSupportNum;
    private Integer fitSupportNum;

    //주거
    private DwellingSimpleInfoDTO dwellingSimpleInfo;

    //인프라
    private List<InfraMajor> infraMajors;

}
