package SDD.smash.recommendation.presentation;

import SDD.smash.common.domain.model.Money;
import SDD.smash.dwelling.domain.model.DwellingType;
import SDD.smash.job.domain.model.JobCode;
import SDD.smash.OpenAI.Converter.AiConverter;
import SDD.smash.OpenAI.Service.AiRecommendService;
import SDD.smash.recommendation.application.dto.RecommendCommand;
import SDD.smash.recommendation.application.dto.RegionRecommendation;
import SDD.smash.recommendation.application.port.in.RecommendRegionUseCase;
import SDD.smash.recommendation.presentation.dto.RecommendAggregateResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * As-Is {@code Apis.Controller.RecommendController} 를 옮긴 것이다.
 * 경로·파라미터명·검증 애노테이션을 그대로 유지한다. {@code dwellingType} 요청 파라미터는
 * 이제 {@code dwelling.domain.model.DwellingType}(새 enum)으로 바인딩된다 — 상수 이름
 * (MONTHLY/JEONSE)이 같아 쿼리스트링 값은 바뀌지 않는다.
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class RecommendController {

    private final RecommendRegionUseCase recommendRegionUseCase;
    private final AiRecommendService aiRecommendService;

    @GetMapping("/recommend")
    public ResponseEntity<RecommendAggregateResponse> recommend(
            @RequestParam(name = "supportChoice", required = true) @NotNull @Min(0) @Max(15) Integer supportChoice,
            @RequestParam(name = "midJobCode", required = false) String midJobCode,
            @RequestParam(name = "dwellingType", required = true) @NotNull(message = "주거 유형은 필수입니다.") DwellingType dwellingType,
            @RequestParam(name = "price", required = true) @NotNull(message = "가격은 필수입니다.") Integer price,
            @RequestParam(name = "infraChoice", required = true) @NotNull(message = "인프라 선택은 필수입니다.") @Min(0) @Max(15) Integer infraChoice,
            @RequestParam(name = "aiUse", defaultValue = "false") boolean aiUse
    ) {
        try {
            log.info("[recommend] recommend 호출됨 sup={}, jobCode={}, dwellingType={}, price={},inf={}",
                    supportChoice, midJobCode, dwellingType, price, infraChoice);
        } catch (Exception e) {
            // As-Is 그대로 — 로그 실패를 요청 처리에 영향 주지 않으려는 방어(원래도 사실상 no-op).
        }

        JobCode jobCode = (midJobCode == null) ? null : JobCode.of(midJobCode);
        RecommendCommand command = new RecommendCommand(supportChoice, jobCode, dwellingType, Money.of(price), infraChoice);

        List<RegionRecommendation> list = recommendRegionUseCase.recommend(command);

        RecommendAggregateResponse response;
        if (aiUse) {
            response = aiRecommendService.summarize(list);
            return ResponseEntity.ok(response);
        }
        response = AiConverter.toResponseList(list, null);
        return ResponseEntity.ok(response);
    }
}
