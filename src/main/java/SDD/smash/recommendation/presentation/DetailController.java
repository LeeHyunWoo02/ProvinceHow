package SDD.smash.recommendation.presentation;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.job.domain.model.JobCode;
import SDD.smash.recommendation.infrastructure.external.AiConverter;
import SDD.smash.recommendation.infrastructure.external.DetailAiSummaryService;
import SDD.smash.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.recommendation.application.port.in.RegionDetailUseCase;
import SDD.smash.recommendation.presentation.dto.DetailResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** As-Is {@code Apis.Controller.DetailController} 를 옮긴 것이다. 경로·파라미터명을 그대로 유지한다. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DetailController {

    private final RegionDetailUseCase regionDetailUseCase;
    private final DetailAiSummaryService detailAiSummaryService;

    @GetMapping("/detail")
    public ResponseEntity<DetailResponse> recommend(
            @RequestParam(name = "sigunguCode", required = true) @NotNull(message = "지역코드는 필수입니다.") String sigunguCode,
            @RequestParam(name = "midJobCode", required = false) String midJobCode,
            @RequestParam(name = "aiUse", defaultValue = "false") boolean aiUse
    ) {
        JobCode jobCode = (midJobCode == null) ? null : JobCode.of(midJobCode);
        RegionDetailInfo detail = regionDetailUseCase.details(SigunguCode.of(sigunguCode), jobCode);

        DetailResponse response;
        if (aiUse) {
            response = detailAiSummaryService.summarize(detail);
            return ResponseEntity.ok(response);
        }

        response = AiConverter.toResponseDTO(detail, null);
        return ResponseEntity.ok(response);
    }
}
