package SDD.smash.domain.support.infrastructure.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 청년정책 API 응답 매핑. As-Is {@code YouthCenterResponse} 를 옮긴 것이다.
 *
 * <p>외부 API 어휘({@code plcyNm}, {@code aplyUrlAddr}, {@code plcyKywdNm})는
 * 이 클래스 밖으로 나가지 않는다. {@code totCount}/페이징 정보는 새 저장 포트가
 * 필요로 하지 않아 옮기지 않았다(저장 개수는 {@code saveAll} 이 목록 크기로 판단한다).
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class YouthCenterApiResponse {

    private int resultCode;
    private String resultMessage;
    private Result result;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        @JsonProperty("youthPolicyList")
        private List<Policy> youthPolicyList;
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
