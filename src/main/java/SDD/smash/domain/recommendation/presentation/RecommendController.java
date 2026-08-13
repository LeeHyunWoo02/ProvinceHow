package SDD.smash.domain.recommendation.presentation;

import SDD.smash.global.domain.model.Money;
import SDD.smash.domain.dwelling.domain.model.DwellingType;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.dto.RecommendCommand;
import SDD.smash.domain.recommendation.application.dto.RegionPick;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;
import SDD.smash.domain.recommendation.application.RecommendRegionService;
import SDD.smash.domain.recommendation.application.port.out.RegionPickProvider;
import SDD.smash.domain.recommendation.presentation.dto.RecommendAggregateResponse;
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
 * 지역 추천 API.
 *
 * <p>주입 대상은 <b>application 계층의 둘</b>이다 — 자기 컨텍스트의 추천 Service 와 AI out-port.
 * {@code infrastructure/external} 의 구현 클래스를 직접 주입하면
 * <b>presentation → infrastructure</b> 역방향 의존이 되므로 하지 않는다.
 * AI 추천은 응답을 꾸미는 표현 계층의 선택 기능({@code aiUse})이므로
 * 유스케이스 안으로 넣지 않고 여기서 포트를 호출한다(architecture-conventions §3.2).
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class RecommendController {

    private final RecommendRegionService recommendRegionService;
    private final RegionPickProvider regionPickProvider;

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

        List<RegionRecommendation> list = recommendRegionService.recommend(command);

        // aiUse=false 면 AI 를 호출하지 않는다(As-Is 분기 유지).
        // AI 호출이 실패하면 포트가 빈 목록을 돌려주므로 aiPick 만 빈 배열이 된다.
        List<RegionPick> picks = aiUse ? regionPickProvider.pick(list) : null;

        RecommendAggregateResponse response = AiConverter.toResponseList(list, picks);
        return ResponseEntity.ok(response);
    }
}
