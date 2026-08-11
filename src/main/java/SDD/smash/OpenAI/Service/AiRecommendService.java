package SDD.smash.OpenAI.Service;

import SDD.smash.Apis.Dto.RecommendAggregateResponse;
import SDD.smash.Apis.Dto.RecommendDTO;
import SDD.smash.common.exception.DomainException;
import SDD.smash.OpenAI.Client.OpenAiClient;
import SDD.smash.OpenAI.Converter.AiConverter;
import SDD.smash.OpenAI.Dto.AiRecommendDTO;
import SDD.smash.OpenAI.Dto.OpenAiMessage;
import SDD.smash.OpenAI.Dto.OpenAiRequest;
import SDD.smash.OpenAI.Dto.OpenAiResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

import static SDD.smash.Util.MapperUtil.extractJson;

@Service
@Slf4j
public class AiRecommendService {
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String MODEL;
    private final Double TEMPERATURE;


    public AiRecommendService(OpenAiClient openAiClient, ObjectMapper objectMapper,
                              @Value("${openai.model}") String model,
                              @Value("${openai.temperature}") Double temperature) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.MODEL = model;
        this.TEMPERATURE = temperature;

    }

    public RecommendAggregateResponse summarize(List<RecommendDTO> recommendList){
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
            String raw = response.getChoices().get(0).getMessage().getContent();
            String jsonOnly = extractJson(raw);
            if(jsonOnly == null){
                return AiConverter.toResponseList(recommendList,null);
            }
            AiRecommendDTO aiDto = objectMapper.readValue(jsonOnly, AiRecommendDTO.class);
            return AiConverter.toResponseList(recommendList, aiDto);
        } catch (JsonProcessingException e) {
            return AiConverter.toResponseList(recommendList,null);
        } catch (DomainException e){
            log.warn("OpenAI API 호출 실패");
            return AiConverter.toResponseList(recommendList, null);
        }
    }

}
