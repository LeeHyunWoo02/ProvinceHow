package SDD.smash.domain.recommendation.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 추천 한 건. As-Is {@code Apis.Dto.AiPickEntry} 를 그대로 옮긴 것이다.
 * {@code OpenAI.Converter.AiConverter} 가 {@code .builder()} 로 만든다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AiPickEntry {

    private String aiPickSigunguCode;
    private String aiPickReason;
}
