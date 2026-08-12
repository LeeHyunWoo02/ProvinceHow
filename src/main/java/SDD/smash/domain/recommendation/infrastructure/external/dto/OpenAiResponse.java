package SDD.smash.domain.recommendation.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiResponse {

    private List<Choice> choices;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Choice{
        private OpenAiMessage message;
    }
}
