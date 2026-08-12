package SDD.smash.domain.recommendation.presentation;

import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.recommendation.application.port.in.RegionCodeUseCase;
import SDD.smash.domain.recommendation.presentation.dto.CodeResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** As-Is {@code Apis.Controller.CodeController} 를 옮긴 것이다. 경로·파라미터명을 그대로 유지한다. */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/code")
public class CodeController {

    private final RegionCodeUseCase regionCodeUseCase;

    @GetMapping("/jobTop")
    public ResponseEntity<List<CodeResponse>> getJobTops() {
        return ResponseEntity.ok(regionCodeUseCase.getAllJobTops().stream().map(CodeResponse::from).toList());
    }

    @GetMapping("/jobMid")
    public ResponseEntity<List<CodeResponse>> getJobMids(
            @RequestParam(required = true) @NotNull(message = "상위 직종코드는 필수입니다.") String topCode
    ) {
        List<CodeResponse> codes = regionCodeUseCase.getAllJobMidsByTop(JobCode.of(topCode)).stream()
                .map(CodeResponse::from).toList();
        return ResponseEntity.ok(codes);
    }

    @GetMapping("/sido")
    public ResponseEntity<List<CodeResponse>> getSidos() {
        return ResponseEntity.ok(regionCodeUseCase.getAllSidos().stream().map(CodeResponse::from).toList());
    }

    @GetMapping("/sigungu")
    public ResponseEntity<List<CodeResponse>> getSigungus(
            @RequestParam(required = true) @NotNull(message = "시/도 코드는 필수입니다.") String sidoCode
    ) {
        List<CodeResponse> codes = regionCodeUseCase.getAllSigungusBySido(SidoCode.of(sidoCode)).stream()
                .map(CodeResponse::from).toList();
        return ResponseEntity.ok(codes);
    }

    @GetMapping("/supportTag")
    public ResponseEntity<List<CodeResponse>> getSupportTag() {
        return ResponseEntity.ok(regionCodeUseCase.getAllSupportTags().stream().map(CodeResponse::from).toList());
    }
}
