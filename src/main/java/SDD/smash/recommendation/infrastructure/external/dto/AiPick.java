package SDD.smash.recommendation.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public class AiPick {
    private String sigunguCode;
    private String reason;
}