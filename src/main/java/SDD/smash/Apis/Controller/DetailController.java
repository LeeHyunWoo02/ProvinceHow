package SDD.smash.Apis.Controller;

import SDD.smash.Apis.Dto.DetailDTO;
import SDD.smash.Apis.Dto.DetailResponseDTO;
import SDD.smash.Apis.Service.DetailService;
import SDD.smash.OpenAI.Converter.AiConverter;
import SDD.smash.OpenAI.Service.DetailAiSummaryService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class DetailController {

    private final DetailService detailService;
    private final DetailAiSummaryService detailAiSummaryService;

    @GetMapping("/detail")
    public ResponseEntity<DetailResponseDTO> recommend(
            @RequestParam(name = "sigunguCode", required = true) @NotNull(message = "지역코드는 필수입니다.") String sigunguCode,
            @RequestParam(name = "midJobCode", required = false) String midJobCode,
            @RequestParam(name = "aiUse", defaultValue = "false") boolean aiUse
    )
    {
        DetailDTO dto = detailService.details(sigunguCode, midJobCode);
        DetailResponseDTO responseDTO;
        if(aiUse){
            responseDTO = detailAiSummaryService.summarize(dto);
            return ResponseEntity.ok(responseDTO);
        }

        responseDTO = AiConverter.toResponseDTO(dto,null);
        return ResponseEntity.ok(responseDTO);
    }
}
