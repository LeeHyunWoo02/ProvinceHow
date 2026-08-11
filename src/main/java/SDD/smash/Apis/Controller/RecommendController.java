package SDD.smash.Apis.Controller;

import SDD.smash.Apis.Dto.RecommendAggregateResponse;
import SDD.smash.Apis.Dto.RecommendDTO;
import SDD.smash.Apis.Service.RecommendService;
import SDD.smash.Dwelling.Entity.DwellingType;
import SDD.smash.OpenAI.Converter.AiConverter;
import SDD.smash.OpenAI.Service.AiRecommendService;
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

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
@Slf4j
public class RecommendController {

    private final RecommendService recommendService;
    private final AiRecommendService aiRecommendService;

    @GetMapping("/recommend")
    public ResponseEntity<RecommendAggregateResponse> recommend(
            @RequestParam(name = "supportChoice", required = true) @NotNull @Min(0) @Max(15) Integer supportChoice,
            @RequestParam(name = "midJobCode", required = false) String midJobCode,
            @RequestParam(name = "dwellingType", required = true) @NotNull(message = "주거 유형은 필수입니다.") DwellingType dwellingType,
            @RequestParam(name = "price", required = true) @NotNull(message = "가격은 필수입니다.") Integer price,
            @RequestParam(name = "infraChoice", required = true) @NotNull(message = "인프라 선택은 필수입니다.") @Min(0) @Max(15) Integer infraChoice,
            @RequestParam(name = "aiUse", defaultValue = "false") boolean aiUse
            )
    {
        try
        {
            log.info("[recommend] recommend 호출됨 sup={}, jobCode={}, dwellingType={}, price={},inf={}", supportChoice, midJobCode, dwellingType, price, infraChoice);
        }catch(Exception e){

        }
        List<RecommendDTO> list = recommendService.recommend(supportChoice, midJobCode, dwellingType, price, infraChoice);
        RecommendAggregateResponse responseDTO;
        if(aiUse){
            responseDTO = aiRecommendService.summarize(list);
            return ResponseEntity.ok(responseDTO);
        }
        responseDTO = AiConverter.toResponseList(list,null);
        return ResponseEntity.ok(responseDTO);
    }
}
