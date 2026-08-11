package SDD.smash.recommendation.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OpenAiMessage {

    @JsonProperty("role")
    private String role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("tools")
    private List<Tool> tools;

    public OpenAiMessage(String role, String content, List<Tool> tools) {
        this.role = role;
        this.content = content;
        this.tools = tools;
    }
}
