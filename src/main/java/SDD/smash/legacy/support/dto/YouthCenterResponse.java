package SDD.smash.legacy.support.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouthCenterResponse {
    private int resultCode;
    private String resultMessage;
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private Paging pagging;
        @JsonProperty("youthPolicyList")
        private List<Policy> youthPolicyList;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Paging {
        private int totCount;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Policy {
        private String plcyNm;
        private String aplyUrlAddr;
        private String plcyKywdNm;
    }
}