package SDD.smash.domain.recommendation.infrastructure.external;

import SDD.smash.domain.recommendation.application.dto.RegionPick;
import SDD.smash.domain.recommendation.application.dto.RegionRecommendation;
import SDD.smash.domain.recommendation.application.port.out.RegionPickProvider;
import SDD.smash.global.exception.DomainException;
import SDD.smash.domain.recommendation.infrastructure.external.dto.AiRecommendDTO;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiMessage;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiRequest;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.List;

import static SDD.smash.global.util.MapperUtil.extractJson;

/**
 * {@link RegionPickProvider} 의 OpenAI 구현. As-Is {@code OpenAI.Service.AiRecommendService} 다.
 *
 * <p><b>바뀐 것은 반환 타입뿐이다.</b> 예전에는 이 클래스가
 * {@code presentation/dto/RecommendAggregateResponse} 를 직접 조립해
 * <b>infrastructure → presentation</b> 역방향 의존을 만들었다. 이제 AI 결과
 * ({@code List<RegionPick>})만 돌려주고, 응답 조립은 {@code presentation/AiConverter} 가 한다.
 * 프롬프트 문자열·모델 파라미터·{@code extractJson} 사용은 그대로다.
 *
 * <p><b>폴백 — 어느 경우든 예외를 밖으로 내보내지 않고</b> 빈 목록을 반환해,
 * 추천 결과는 정상 응답되고 {@code aiPick} 만 빈 배열이 되게 한다.
 * <ol>
 *   <li>{@code choices} 가 null/빈 (응답에 선택지가 없음)</li>
 *   <li>{@code extractJson} 이 null (응답에서 JSON 을 못 찾음)</li>
 *   <li>{@code JsonProcessingException} (직렬화/역직렬화 실패)</li>
 *   <li>{@code RestClientException}/{@code DomainException}
 *       (OpenAI 4xx/5xx·타임아웃 — {@code OpenAiClient}·{@code RestClient} 가 던진다)</li>
 * </ol>
 *
 * <p>빈 이름을 {@code aiRecommendService} 로 고정한다 — 클래스명만 컨벤션에 맞게 바꾸고
 * 스프링 빈 식별자는 As-Is 와 동일하게 남긴다.
 */
@Component("aiRecommendService")
@Slf4j
public class OpenAiRegionPickApiAdapter implements RegionPickProvider {
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String MODEL;
    private final Double TEMPERATURE;


    public OpenAiRegionPickApiAdapter(OpenAiClient openAiClient, ObjectMapper objectMapper,
                              @Value("${openai.model}") String model,
                              @Value("${openai.temperature}") Double temperature) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.MODEL = model;
        this.TEMPERATURE = temperature;

    }

    @Override
    public List<RegionPick> pick(List<RegionRecommendation> recommendList){
        try{
            String json = objectMapper.writeValueAsString(recommendList);


            OpenAiMessage system = new OpenAiMessage(
                    "system",
                    "당신은 한국어로 간결하고 사실 기반으로 답하는 AI 비서입니다. " +
                            "반환은 반드시 순수 JSON 하나의 객체만 출력하세요.",
                    null
            );

            String userPrompt = """
                아래는 사용자 맞춤 지역 추천 데이터(JSON 배열)입니다.
                'score' 값은 무시하고, 나머지 정보(일자리/지원/주거/인프라)를 종합적으로 고려하여
                사용자에게 적합한 시군구 3곳을 추천하세요.
                
                일자리 판단 시에는 전체 일자리(totalJobInfo)가 아니라 '맞춤 일자리(fitJobInfo)의 개수'를 기준으로 평가하세요.
                다만 fitJobInfo가 없거나 개수가 적은 경우라도, **지원 정책, 주거 여건, 인프라가 매우 우수하다면**
                그 지역을 예외적으로 긍정적으로 추천할 수 있습니다.
                
                단, 추천 이유(reason)를 작성할 때는 '맞춤 일자리 정보 없음', '일자리 부족', '데이터 부족' 등의 부정적인 표현은 사용하지 마세요.
                검색 결과가 부족하거나 fitJobInfo가 null이라도, 그 사실을 언급하지 말고
                대신 지원 정책, 주거비, 인프라 등 다른 강점을 중심으로 자연스럽게 설명하세요.

                출력 규칙:
                1. 출력은 반드시 순수 JSON 객체 하나로만 구성합니다.
                2. **마크다운 문법(예: ```json, ``` , `, *, -, # 등)은 절대 포함하지 마세요.**
                3. JSON 외의 설명문, 서문, 코드블록, 인용문, 주석 등은 포함하지 마세요.
                4. JSON 필드 이름과 자료형은 아래 스키마를 정확히 따르세요.
                5. recommendations 배열은 정확히 3개 요소를 포함해야 합니다.
                6. 입력 배열에 존재하지 않는 sigunguCode는 절대 반환하지 마세요.

                스키마(JSON):
                {
                  "recommendations": [
                    { "sigunguCode": "string", "reason": "string" },
                    { "sigunguCode": "string", "reason": "string" },
                    { "sigunguCode": "string", "reason": "string" }
                  ]
                }

                입력(JSON 배열):
                %s
                """.formatted(json);
            OpenAiMessage user = new OpenAiMessage("user", userPrompt, null);
            OpenAiRequest request = new OpenAiRequest(MODEL, List.of(system, user),TEMPERATURE);

            OpenAiResponse response = openAiClient.getChatCompletion(request);
            if (response.getChoices() == null || response.getChoices().isEmpty()) {
                return List.of();                       // 폴백 ① — choices 가 비면 빈 목록
            }
            String raw = response.getChoices().get(0).getMessage().getContent();
            String jsonOnly = extractJson(raw);
            if(jsonOnly == null){
                return List.of();                       // 폴백 ② — 응답에서 JSON 을 못 찾음
            }
            AiRecommendDTO aiDto = objectMapper.readValue(jsonOnly, AiRecommendDTO.class);
            return toPicks(aiDto);
        } catch (JsonProcessingException e) {
            return List.of();                           // 폴백 ③ — 직렬화/역직렬화 실패
        } catch (RestClientException | DomainException e){
            // OpenAI 호출 실패(4xx/5xx·타임아웃 등). 비밀값·URL 은 남기지 않는다.
            log.warn("OpenAI 지역 추천 호출 실패 - 빈 목록으로 폴백");
            return List.of();                           // 폴백 ④
        }
    }

    /**
     * 외부 LLM 응답 스키마를 application DTO 로 번역한다.
     * As-Is {@code AiConverter.toResponseList} 안에 있던
     * {@code aiRecommendDTO == null || getRecommendations() == null → List.of()} 판정을
     * 그대로 옮겨온 것이다.
     */
    private List<RegionPick> toPicks(AiRecommendDTO aiRecommendDTO) {
        if (aiRecommendDTO == null || aiRecommendDTO.getRecommendations() == null) {
            return List.of();
        }
        return aiRecommendDTO.getRecommendations().stream()
                .map(p -> new RegionPick(p.getSigunguCode(), p.getReason()))
                .toList();
    }

}
