package SDD.smash.recommendation.application;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.address.application.port.in.PopulationQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.dwelling.application.port.in.DwellingQueryUseCase;
import SDD.smash.infra.application.port.in.InfraQueryUseCase;
import SDD.smash.job.application.port.in.JobQueryUseCase;
import SDD.smash.job.domain.model.JobCode;
import SDD.smash.recommendation.application.dto.DwellingInfoSummary;
import SDD.smash.recommendation.application.dto.IndustryDetailItem;
import SDD.smash.recommendation.application.dto.JobInfoSummary;
import SDD.smash.recommendation.application.dto.MajorInfraSummaryItem;
import SDD.smash.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.recommendation.application.dto.SupportPolicyItem;
import SDD.smash.recommendation.application.dto.SupportPolicyListSummary;
import SDD.smash.recommendation.application.port.in.RegionDetailUseCase;
import SDD.smash.support.application.port.in.SupportQueryUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지역 상세 조회 유스케이스. As-Is {@code DetailService.details} 를 옮긴 것이다.
 *
 * <p>시군구 존재 검증과 코드명 조회는 address in-port로, 직종 코드 존재 검증은
 * job in-port({@code getJobInfo(sigungu, jobCode)} 내부)가 각각 담당한다 —
 * As-Is가 {@code DetailService} 안에서 직접 하던 검증을 각 컨텍스트로 내렸을 뿐
 * 던지는 {@code ErrorCode} 는 동일하다.
 */
@Service
@RequiredArgsConstructor
public class RegionDetailService implements RegionDetailUseCase {

    private final AddressQueryUseCase addressQueryUseCase;
    private final PopulationQueryUseCase populationQueryUseCase;
    private final JobQueryUseCase jobQueryUseCase;
    private final SupportQueryUseCase supportQueryUseCase;
    private final DwellingQueryUseCase dwellingQueryUseCase;
    private final InfraQueryUseCase infraQueryUseCase;

    @Override
    public RegionDetailInfo details(SigunguCode sigunguCode, JobCode midJobCode) {

        addressQueryUseCase.checkSigunguExistsOrThrow(sigunguCode);

        // As-Is 는 코드명 조회 전에 midJobCode 존재를 미리 검증했다. 여기서는
        // getJobInfo(sigungu, jobCode) 호출 시점에 같은 검증이 일어나므로 순서를 먼저 당겨온다.
        JobInfoSummary fitJobInfo = JobInfoSummary.from(jobQueryUseCase.getJobInfo(sigunguCode, midJobCode));

        var regionCode = addressQueryUseCase.getRegionCode(sigunguCode).orElseThrow();

        return RegionDetailInfo.builder()
                .sidoCode(regionCode.sidoCode().value())
                .sidoName(regionCode.sidoName())
                .sigunguCode(regionCode.sigunguCode().value())
                .sigunguName(regionCode.sigunguName())

                .population(populationQueryUseCase.getPopulation(sigunguCode))

                .totalJobInfo(JobInfoSummary.from(jobQueryUseCase.getJobInfo(sigunguCode)))
                .fitJobInfo(fitJobInfo)

                .totalSupportNum(supportQueryUseCase.getAllSupportCount(sigunguCode))
                .supportList(new SupportPolicyListSummary(
                        supportQueryUseCase.getAllSupportPolicies(sigunguCode).stream()
                                .map(SupportPolicyItem::from)
                                .toList()))

                .dwellingInfo(DwellingInfoSummary.from(dwellingQueryUseCase.getDwellingInfo(sigunguCode)))

                .infraDetails(infraQueryUseCase.getInfraDetails(sigunguCode).stream()
                        .map(IndustryDetailItem::from)
                        .toList())
                .infraMajors(infraQueryUseCase.getMajorInfraSummaries(sigunguCode).stream()
                        .map(MajorInfraSummaryItem::from)
                        .toList())
                .build();
    }
}
