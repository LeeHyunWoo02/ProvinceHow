package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.NonCapitalJobRankingService;
import SDD.smash.domain.job.application.RegionJobStatisticsQueryService;
import SDD.smash.domain.job.application.dto.JobCategoryView;
import SDD.smash.domain.job.application.dto.NonCapitalRankView;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsTrendPointSummary;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 고용행정통계 화면 확장 조회 유스케이스(직종 분해 등).
 *
 * <p>지역 상세 첫 화면({@code RegionDetailService})은 시군구 합계만 싣는다. 여기서는 직종
 * 대분류 분해처럼 통계 전용 화면이 필요로 하는 조합을 만든다. 시군구 존재 검증은 address 의
 * application Service 가, 통계·직종명·비수도권 백분위 조회는 job 의 application Service 가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class RegionJobStatisticsDetailService {

    private final AddressQueryService addressQueryService;
    private final RegionJobStatisticsQueryService regionJobStatisticsQueryService;
    private final NonCapitalJobRankingService nonCapitalJobRankingService;
    private final JobQueryService jobQueryService;

    /**
     * 최신 기준월 한 시군구의 직종 대분류별 통계. 존재하지 않는 시군구면 예외,
     * 존재하지만 미적재(또는 시 레벨)면 기준월 없이 빈 목록이다.
     *
     * <p>통계 미적재는 예외가 아니지만 <b>{@code midJobCode} 는 다르다</b> — 사용자가 명시적으로
     * 준 코드라 유효하지 않으면 {@code /api/detail} 과 같은 규칙으로 {@code JOB_CODE_NOT_FOUND} 다.
     * 조용히 무시하면 선택이 사라진 화면이 정상처럼 보인다.
     *
     * @param midJobCode 사용자가 고른 직종 <b>중분류</b>. {@code null} 이면 선택 표시 없이 13종을 준다
     */
    public RegionJobStatisticsByJobSummary byJob(SigunguCode sigunguCode, JobCode midJobCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        // 통계 조회보다 먼저 검증한다. 미적재 지역이라고 해서 잘못된 직종 코드가 통과하면 안 된다.
        String selectedJobTopCode = (midJobCode == null)
                ? null
                : jobQueryService.getTopCodeOfSubOrThrow(midJobCode).value();

        List<RegionJobStatisticsView> views =
                regionJobStatisticsQueryService.getLatestStatisticsOfRegion(sigunguCode);
        if (views.isEmpty()) {
            return RegionJobStatisticsByJobSummary.empty(selectedJobTopCode);
        }

        NonCapitalRankView totalRank =
                nonCapitalJobRankingService.getRegionRank(sigunguCode).orElse(null);
        Map<String, NonCapitalRankView> rankByJobMajorCode =
                nonCapitalJobRankingService.getRegionRankByJob(sigunguCode);

        return RegionJobStatisticsByJobSummary.from(
                views, jobMajorNames(), selectedJobTopCode, totalRank, rankByJobMajorCode);
    }

    /**
     * 한 시군구의 월별 고용통계 추세(직종 13종 합계, 월 오름차순). 존재하지 않는 시군구면 예외,
     * 존재하지만 미적재면 빈 목록이다. 폴딩·최근 N개월 절단은 job 이 담당한다.
     */
    public List<RegionJobStatisticsTrendPointSummary> trend(SigunguCode sigunguCode, int months) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        return regionJobStatisticsQueryService.getRegionTrend(sigunguCode, months).stream()
                .map(RegionJobStatisticsTrendPointSummary::from)
                .toList();
    }

    /** 직종 대분류 코드→이름. 순서는 결과 정렬이 따로 하므로 여기서는 조회 순서를 유지한다. */
    private Map<String, String> jobMajorNames() {
        Map<String, String> nameByCode = new LinkedHashMap<>();
        for (JobCategoryView category : jobQueryService.getAllTopCategories()) {
            nameByCode.put(category.code().value(), category.name());
        }
        return nameByCode;
    }
}
