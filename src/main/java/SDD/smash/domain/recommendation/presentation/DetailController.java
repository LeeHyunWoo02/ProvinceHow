package SDD.smash.domain.recommendation.presentation;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.domain.recommendation.application.RegionDetailService;
import SDD.smash.domain.recommendation.application.port.out.RegionSummaryProvider;
import SDD.smash.domain.recommendation.presentation.dto.DetailResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지역 상세 조회 API.
 *
 * <p>주입 대상은 application 계층의 둘이다 — 자기 컨텍스트의 조회 Service 와 AI 요약 out-port.
 * AI 요약은 응답을 꾸미는 표현 계층의 선택 기능({@code aiUse})이라 유스케이스 안으로
 * 넣지 않고 여기서 포트를 호출한다(architecture-conventions §3.2).
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DetailController {

    private final RegionDetailService regionDetailService;
    private final RegionSummaryProvider regionSummaryProvider;

    @GetMapping("/detail")
    public ResponseEntity<DetailResponse> recommend(
            @RequestParam(name = "sigunguCode", required = true) @NotNull(message = "지역코드는 필수입니다.") String sigunguCode,
            @RequestParam(name = "midJobCode", required = false) String midJobCode,
            @RequestParam(name = "aiUse", defaultValue = "false") boolean aiUse
    ) {
        JobCode jobCode = (midJobCode == null) ? null : JobCode.of(midJobCode);
        RegionDetailInfo detail = regionDetailService.details(SigunguCode.of(sigunguCode), jobCode);

        // aiUse=false 면 AI 를 호출하지 않는다(As-Is 분기 유지).
        // AI 호출이 실패하면 포트가 null 을 돌려주므로 aiSummary 만 빈다.
        String aiSummary = aiUse ? regionSummaryProvider.summarize(detail) : null;

        DetailResponse response = AiConverter.toResponseDTO(detail, aiSummary);
        return ResponseEntity.ok(response);
    }
}
