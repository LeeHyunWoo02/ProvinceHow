package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.address.application.PopulationQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.application.DwellingQueryService;
import SDD.smash.domain.infra.application.InfraQueryService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.JobVacancyQueryService;
import SDD.smash.domain.job.application.RegionJobProfileQueryService;
import SDD.smash.domain.job.application.RegionJobStatisticsQueryService;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.DwellingInfoSummary;
import SDD.smash.domain.recommendation.application.dto.DwellingTypeItem;
import SDD.smash.domain.recommendation.application.dto.IndustryDetailItem;
import SDD.smash.domain.recommendation.application.dto.JobInfoSummary;
import SDD.smash.domain.recommendation.application.dto.JobVacancyItem;
import SDD.smash.domain.recommendation.application.dto.RegionJobProfileItem;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsSummary;
import SDD.smash.domain.recommendation.application.dto.MajorInfraSummaryItem;
import SDD.smash.domain.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.domain.recommendation.application.dto.SupportPolicyItem;
import SDD.smash.domain.recommendation.application.dto.SupportPolicyListSummary;
import SDD.smash.domain.support.application.SupportQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 지역 상세 조회 유스케이스.
 *
 * <p>시군구 존재 검증과 코드명 조회는 address 의 application Service 로, 직종 코드 존재
 * 검증은 job 의 application Service({@code getJobInfo(sigungu, jobCode)} 내부)가 각각
 * 담당한다 — 검증을 각 컨텍스트가 소유하며 던지는 {@code ErrorCode} 는 그 컨텍스트의 것이다.
 */
@Service
@RequiredArgsConstructor
public class RegionDetailService {

    private final AddressQueryService addressQueryService;
    private final PopulationQueryService populationQueryService;
    private final JobQueryService jobQueryService;
    private final JobVacancyQueryService jobVacancyQueryService;
    private final RegionJobProfileQueryService regionJobProfileQueryService;
    private final RegionJobStatisticsQueryService regionJobStatisticsQueryService;
    private final SupportQueryService supportQueryService;
    private final DwellingQueryService dwellingQueryService;
    private final InfraQueryService infraQueryService;

    public RegionDetailInfo details(SigunguCode sigunguCode, JobCode midJobCode) {

        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        // 코드명 조회보다 midJobCode 검증이 먼저다. getJobInfo(sigungu, jobCode) 호출 시점에
        // 직종 코드 검증이 일어나므로 순서를 앞으로 당겨온다.
        JobInfoSummary fitJobInfo = JobInfoSummary.from(jobQueryService.getJobInfo(sigunguCode, midJobCode));

        var regionCode = addressQueryService.getRegionCode(sigunguCode).orElseThrow();

        return RegionDetailInfo.builder()
                .sidoCode(regionCode.sidoCode().value())
                .sidoName(regionCode.sidoName())
                .sigunguCode(regionCode.sigunguCode().value())
                .sigunguName(regionCode.sigunguName())

                .population(populationQueryService.getPopulation(sigunguCode))

                .totalJobInfo(JobInfoSummary.from(jobQueryService.getJobInfo(sigunguCode)))
                .fitJobInfo(fitJobInfo)

                .jobVacancies(jobVacancyQueryService.getVacancies(sigunguCode).stream()
                        .map(JobVacancyItem::from)
                        .toList())

                .regionJobProfile(RegionJobProfileItem.from(
                        regionJobProfileQueryService.getProfile(sigunguCode)))

                .jobStatistics(jobStatisticsOf(sigunguCode))

                .totalSupportNum(supportQueryService.getAllSupportCount(sigunguCode))
                .supportList(new SupportPolicyListSummary(
                        supportQueryService.getAllSupportPolicies(sigunguCode).stream()
                                .map(SupportPolicyItem::from)
                                .toList()))

                .dwellingInfo(DwellingInfoSummary.from(dwellingQueryService.getDwellingInfo(sigunguCode)))
                .dwellingByType(dwellingQueryService.getDwellingByType(sigunguCode).stream()
                        .map(DwellingTypeItem::from)
                        .toList())

                .infraDetails(infraQueryService.getInfraDetails(sigunguCode).stream()
                        .map(IndustryDetailItem::from)
                        .toList())
                .infraMajors(infraQueryService.getMajorInfraSummaries(sigunguCode).stream()
                        .map(MajorInfraSummaryItem::from)
                        .toList())
                .build();
    }

    /**
     * 최신 기준월의 고용행정통계를 시군구 단위로 접는다. 적재 전이면 {@code null} 이다
     * ({@code regionJobProfile} 과 같은 규칙 — 없는 값을 0 으로 채우지 않는다).
     *
     * <p>job 이 여는 조회는 "최신월 전 시군구" 단위라 여기서 해당 시군구만 걸러 쓴다.
     */
    private RegionJobStatisticsSummary jobStatisticsOf(SigunguCode sigunguCode) {
        return RegionJobStatisticsSummary.from(
                regionJobStatisticsQueryService.getLatestStatistics(null)   // null = 직종 전체
                        .stream()
                        .filter(view -> sigunguCode.equals(view.sigunguCode()))
                        .toList());
    }
}
