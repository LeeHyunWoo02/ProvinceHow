package SDD.smash.Apis.Service;

import SDD.smash.legacy.address.Repository.SigunguRepository;
import SDD.smash.legacy.address.Service.AddressVerifyService;
import SDD.smash.legacy.address.Service.PopulationService;
import SDD.smash.Apis.Dto.CodeNameDTO;
import SDD.smash.Apis.Dto.DetailDTO;
import SDD.smash.legacy.dwelling.Service.DwellingService;
import SDD.smash.common.exception.ErrorCode;
import SDD.smash.common.exception.DomainException;
import SDD.smash.legacy.infra.Service.InfraService;
import SDD.smash.legacy.job.Repository.JobCodeMiddleRepository;
import SDD.smash.legacy.job.Service.JobService;
import SDD.smash.Support.service.SupportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DetailService {

    private final JobService jobService;
    private final DwellingService dwellingService;
    private final SupportService supportService;
    private final InfraService infraService;
    private final PopulationService populationService;

    private final AddressVerifyService addressVerifyService;
    private final SigunguRepository sigunguRepository;
    private final JobCodeMiddleRepository jobCodeMiddleRepository;

    @Transactional(readOnly = true)
    public DetailDTO details(String sigunguCode, String midJobCode)
    {
        //시군구 코드 검증
        addressVerifyService.checkSigunguCodeOrThrow(sigunguCode);

        //midJobCode 검증
        if (midJobCode != null && !jobCodeMiddleRepository.existsByCode(midJobCode))
            throw new DomainException(ErrorCode.JOB_CODE_NOT_FOUND, "유효하지 않은 직종코드");

        CodeNameDTO codeName = sigunguRepository.findCodeNameBySigunguCode(sigunguCode);

        return DetailDTO.builder()
                .sidoCode(codeName.getSidoCode())
                .sidoName(codeName.getSidoName())
                .sigunguCode(codeName.getSigunguCode())
                .sigunguName(codeName.getSigunguName())

                .population(populationService.getPopulationBySigunguCode(sigunguCode))

                .totalJobInfo(jobService.getJobInfoBySigungu(sigunguCode))
                .fitJobInfo(jobService.getJobInfoBySigunguAndJobCode(sigunguCode, midJobCode))

                .totalSupportNum(supportService.getAllSupportNum(sigunguCode))
                .supportList(supportService.getAllSupportList(sigunguCode))

                .dwellingInfo(dwellingService.getDwellingInfo(sigunguCode))

                .infraDetails(infraService.getInfraDetails(sigunguCode))
                .infraMajors(infraService.getMajorInfraNumAndScore(sigunguCode))
                .build();
    }
}
