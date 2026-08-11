package SDD.smash.recommendation.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OpenAiRequest {

    @JsonProperty("model")
    private String model;

    @JsonProperty("messages")
    private List<OpenAiMessage> messages;

    @JsonProperty("temperature")
    private Double temperature;

    public OpenAiRequest(String model, List<OpenAiMessage> messages,
                         Double temperature) {
        this.model = model;
        this.messages = messages;
        this.temperature = temperature;
    }
}
