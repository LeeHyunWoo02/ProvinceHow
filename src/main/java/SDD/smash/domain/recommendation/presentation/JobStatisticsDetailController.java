package SDD.smash.domain.recommendation.presentation;

import SDD.smash.domain.recommendation.application.RegionJobStatisticsDetailService;
import SDD.smash.domain.recommendation.application.dto.RegionJobStatisticsByJobSummary;
import SDD.smash.domain.recommendation.presentation.dto.RegionJobStatisticsByJobResponse;
import SDD.smash.global.domain.model.SigunguCode;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
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

    /** 최신 기준월 한 시군구의 직종 대분류 13종 분해. */
    @GetMapping("/byJob")
    public ResponseEntity<RegionJobStatisticsByJobResponse> byJob(
            @RequestParam(name = "sigunguCode") @NotNull(message = "지역코드는 필수입니다.") String sigunguCode) {

        RegionJobStatisticsByJobSummary summary =
                regionJobStatisticsDetailService.byJob(SigunguCode.of(sigunguCode));
        return ResponseEntity.ok(RegionJobStatisticsByJobResponse.from(summary));
    }
}
