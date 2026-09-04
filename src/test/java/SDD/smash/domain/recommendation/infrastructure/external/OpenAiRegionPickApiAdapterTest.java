package SDD.smash.domain.recommendation.infrastructure.external;

import SDD.smash.domain.recommendation.application.dto.RegionPick;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiResponse;
import SDD.smash.global.exception.DomainException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static SDD.smash.global.exception.ErrorCode.OPENAI_SERVER_ERROR;
import static SDD.smash.global.exception.ErrorCode.OPENAI_TOKEN_EXPIRED;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * {@link OpenAiRegionPickApiAdapter} 의 폴백 계약 테스트.
 *
 * <p>어떤 실패 경로에서도 예외를 컨트롤러로 올리지 않고 빈 목록을 반환하는지 검증한다
 * — (1) 5xx/429, (2) 읽기 타임아웃(ResourceAccessException), (3) 빈/null choices.
 */
@ExtendWith(MockitoExtension.class)
class OpenAiRegionPickApiAdapterTest {

    @Mock OpenAiClient openAiClient;

    private OpenAiRegionPickApiAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new OpenAiRegionPickApiAdapter(openAiClient, new ObjectMapper(), "gpt-test", 0.3);
    }

    @Test
    @DisplayName("OpenAI 5xx(OPENAI_SERVER_ERROR)면 예외 없이 빈 목록을 반환한다")
    void returnsEmptyOnServerError() {
        given(openAiClient.getChatCompletion(any()))
                .willThrow(new DomainException(OPENAI_SERVER_ERROR, "boom"));

        List<RegionPick> result = adapter.pick(recommendList());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("OpenAI 429(OPENAI_TOKEN_EXPIRED)면 예외 없이 빈 목록을 반환한다")
    void returnsEmptyOnTokenExpired() {
        given(openAiClient.getChatCompletion(any()))
                .willThrow(new DomainException(OPENAI_TOKEN_EXPIRED, "rate limit"));

        List<RegionPick> result = adapter.pick(recommendList());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("읽기 타임아웃(ResourceAccessException)이면 예외 없이 빈 목록을 반환한다")
    void returnsEmptyOnReadTimeout() {
        given(openAiClient.getChatCompletion(any()))
                .willThrow(new ResourceAccessException("read timed out"));

        List<RegionPick> result = adapter.pick(recommendList());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("choices 가 null 이면 예외 없이 빈 목록을 반환한다")
    void returnsEmptyWhenChoicesNull() {
        OpenAiResponse response = mock(OpenAiResponse.class);
        given(response.getChoices()).willReturn(null);
        given(openAiClient.getChatCompletion(any())).willReturn(response);

        List<RegionPick> result = adapter.pick(recommendList());

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("choices 가 빈 목록이면 예외 없이 빈 목록을 반환한다")
    void returnsEmptyWhenChoicesEmpty() {
        OpenAiResponse response = mock(OpenAiResponse.class);
        given(response.getChoices()).willReturn(List.of());
        given(openAiClient.getChatCompletion(any())).willReturn(response);

        List<RegionPick> result = adapter.pick(recommendList());

        assertThat(result).isEmpty();
    }

    private static List<RegionRecommendation> recommendList() {
        return List.of(RegionRecommendation.builder().sigunguCode("11680").score(90).build());
    }
}
