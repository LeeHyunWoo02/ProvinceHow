package SDD.smash.domain.recommendation.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.domain.job.application.JobQueryService;
import SDD.smash.domain.job.application.RegionJobStatisticsQueryService;
import SDD.smash.domain.job.application.dto.JobCategoryView;
import SDD.smash.domain.job.application.dto.RegionJobStatisticsView;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
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
 * application Service 가, 통계·직종명 조회는 job 의 application Service 가 담당한다.
 */
@Service
@RequiredArgsConstructor
public class RegionJobStatisticsDetailService {

    private final AddressQueryService addressQueryService;
    private final RegionJobStatisticsQueryService regionJobStatisticsQueryService;
    private final JobQueryService jobQueryService;

    /**
     * 최신 기준월 한 시군구의 직종 대분류별 통계. 존재하지 않는 시군구면 예외,
     * 존재하지만 미적재(또는 시 레벨)면 기준월 없이 빈 목록이다.
     */
    public RegionJobStatisticsByJobSummary byJob(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        List<RegionJobStatisticsView> views =
                regionJobStatisticsQueryService.getLatestStatisticsOfRegion(sigunguCode);

        return RegionJobStatisticsByJobSummary.from(views, jobMajorNames());
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
