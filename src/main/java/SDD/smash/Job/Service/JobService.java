package SDD.smash.Job.Service;

import SDD.smash.Address.Service.AddressVerifyService;
import SDD.smash.Exception.Code.ErrorCode;
import SDD.smash.Exception.Exception.BusinessException;
import SDD.smash.Job.Dto.JobInfoDTO;
import SDD.smash.Job.Repository.JobCodeMiddleRepository;
import SDD.smash.Job.Repository.JobCountRepository;
import lombok.RequiredArgsConstructor;
<<<<<<< HEAD
import org.springframework.beans.factory.annotation.Value;
=======
>>>>>>> origin/Backup/main
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JobService {

<<<<<<< HEAD
    @Value("${worknet.base-url}")
    private String baseUrl;

    @Value("${worknet.path}")
    private String path;
=======
    private static final String BASE_URL = "https://work24.go.kr";
    private static final String PATH = "/wk/a/b/1200/retriveDtlEmpSrchList.do";
>>>>>>> origin/Backup/main

    private final AddressVerifyService addressVerifyService;

    private final JobCountRepository jobCountRepository;
    private final JobCodeMiddleRepository jobCodeMiddleRepository;


    /**
     * 해당 시군구의 전체 일자리 개수와 해당 목록 url을  반환
     */
    public JobInfoDTO getJobInfoBySigungu(String sigunguCode)
    {
        // 시군구 코드 존재 확인
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        JobInfoDTO jobInfo = jobCountRepository.findJobInfo(sigunguCode);
        if(jobInfo == null) return null;

        jobInfo.setUrl(generateUrl(sigunguCode));
        return jobInfo;
    }

    /**
     * 해당 시군구의 직종 코드의 일자리 개수와 해당 목록 url을 반환
     */
    public JobInfoDTO getJobInfoBySigunguAndJobCode(String sigunguCode, String jobMidCode)
    {
        if(jobMidCode == null) return null;

        //시군구 코드 존재 확인
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        //jobcode 존재 확인
        if(jobMidCode != null && !jobCodeMiddleRepository.existsByCode(jobMidCode))
        {
            throw new BusinessException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종 코드입니다.");
        }

        JobInfoDTO jobInfo = jobCountRepository.findJobInfoByCode(sigunguCode, jobMidCode);
        if(jobInfo == null) return null;

        jobInfo.setUrl(generateFitUrl(sigunguCode, jobMidCode));
        return jobInfo;
    }



    private String generateUrl(String sigunguCode)
    {
<<<<<<< HEAD
        return baseUrl + path
                + "?region=" + sigunguCode
=======
        return BASE_URL + PATH
                + "&region=" + sigunguCode
>>>>>>> origin/Backup/main
                + "&resultCnt=10"
                + "&pageIndex=1";
    }

    private String generateFitUrl(String sigunguCode, String JobMiddleCode)
    {
<<<<<<< HEAD
        return baseUrl + path
=======
        return BASE_URL + PATH
>>>>>>> origin/Backup/main
                + "?occupation=" + JobMiddleCode
                + "&region=" + sigunguCode
                + "&resultCnt=10"
                + "&pageIndex=1";
    }
}
