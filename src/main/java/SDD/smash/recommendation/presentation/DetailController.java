package SDD.smash.recommendation.presentation;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.job.domain.model.JobCode;
import SDD.smash.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.recommendation.application.port.in.RegionDetailUseCase;
import SDD.smash.recommendation.application.port.out.RegionSummaryProvider;
import SDD.smash.recommendation.presentation.dto.DetailResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * As-Is {@code Apis.Controller.DetailController} 를 옮긴 것이다. 경로·파라미터명을 그대로 유지한다.
 *
 * <p>주입 대상은 application 계층의 포트 2개다(조회 in-port + AI 요약 out-port).
 * 근거는 {@code RecommendController} 주석과 같다.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DetailController {

    private final RegionDetailUseCase regionDetailUseCase;
    private final RegionSummaryProvider regionSummaryProvider;

    @GetMapping("/detail")
    public ResponseEntity<DetailResponse> recommend(
            @RequestParam(name = "sigunguCode", required = true) @NotNull(message = "지역코드는 필수입니다.") String sigunguCode,
            @RequestParam(name = "midJobCode", required = false) String midJobCode,
            @RequestParam(name = "aiUse", defaultValue = "false") boolean aiUse
    ) {
        JobCode jobCode = (midJobCode == null) ? null : JobCode.of(midJobCode);
        RegionDetailInfo detail = regionDetailUseCase.details(SigunguCode.of(sigunguCode), jobCode);

        // aiUse=false 면 AI 를 호출하지 않는다(As-Is 분기 유지).
        // AI 호출이 실패하면 포트가 null 을 돌려주므로 aiSummary 만 빈다.
        String aiSummary = aiUse ? regionSummaryProvider.summarize(detail) : null;

        DetailResponse response = AiConverter.toResponseDTO(detail, aiSummary);
        return ResponseEntity.ok(response);
    }
}
