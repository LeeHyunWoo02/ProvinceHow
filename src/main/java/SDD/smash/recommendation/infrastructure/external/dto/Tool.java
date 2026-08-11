package SDD.smash.recommendation.infrastructure.external.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Tool {
    @JsonProperty("type")
    private String type;

    @JsonProperty("user_location")
    private UserLocation userLocation;
}
