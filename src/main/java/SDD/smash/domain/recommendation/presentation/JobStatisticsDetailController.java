package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.recommendation.application.RegionJobStatisticsDetailService;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsTrendPointSummary;
import SDD.smash.domain.recommendation.presentation.dto.RegionJobStatisticsByJobResponse;
import SDD.smash.domain.recommendation.presentation.dto.RegionJobStatisticsTrendResponse;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.global.domain.model.SigunguCode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 고용행정통계 화면 확장 조회 API. 지역 상세 첫 화면 밖의 통계 전용 조회를 담는다.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/detail/jobStatistics")
public class JobStatisticsDetailController {

    private final RegionJobStatisticsDetailService regionJobStatisticsDetailService;

    /**
     * 최신 기준월 한 시군구의 직종 대분류 13종 분해.
     *
     * @param midJobCode 직종 <b>중분류</b> 코드(선택). 주면 그 중분류가 속한 대분류 item 에
     *                   {@code isSelected=true} 가 붙고 최상단 {@code selectedJobTopCode} 도 채워진다
     */
    @GetMapping("/byJob")
    public ResponseEntity<RegionJobStatisticsByJobResponse> byJob(
            @RequestParam(name = "sigunguCode") @NotNull(message = "지역코드는 필수입니다.") String sigunguCode,
            @RequestParam(name = "midJobCode", required = false) String midJobCode) {

        JobCode jobCode = (midJobCode == null || midJobCode.isBlank()) ? null : JobCode.of(midJobCode);

        RegionJobStatisticsByJobSummary summary =
                regionJobStatisticsDetailService.byJob(SigunguCode.of(sigunguCode), jobCode);
        return ResponseEntity.ok(RegionJobStatisticsByJobResponse.from(summary));
    }

    /** 한 시군구의 월별 고용통계 추세(직종 13종 합계). 최근 {@code months} 개월만 준다. */
    @GetMapping("/trend")
    public ResponseEntity<RegionJobStatisticsTrendResponse> trend(
            @RequestParam(name = "sigunguCode") @NotNull(message = "지역코드는 필수입니다.") String sigunguCode,
            @RequestParam(name = "months", defaultValue = "36") @Min(1) @Max(120) int months) {

        List<RegionJobStatisticsTrendPointSummary> points =
                regionJobStatisticsDetailService.trend(SigunguCode.of(sigunguCode), months);
        return ResponseEntity.ok(RegionJobStatisticsTrendResponse.of(sigunguCode, points));
    }
}
