package SDD.smash.domain.recommendation.infrastructure.external;

import SDD.smash.domain.recommendation.application.dto.RegionDetailInfo;
import SDD.smash.domain.recommendation.application.port.out.RegionSummaryProvider;
import SDD.smash.global.exception.DomainException;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiMessage;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiRequest;
import SDD.smash.domain.recommendation.infrastructure.external.dto.OpenAiResponse;
import SDD.smash.domain.recommendation.infrastructure.external.dto.Tool;
import SDD.smash.domain.recommendation.infrastructure.external.dto.UserLocation;
import SDD.smash.recommendation.infrastructure.external.dto.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link RegionSummaryProvider} 의 OpenAI 구현.
 * As-Is {@code OpenAI.Service.DetailAiSummaryService} 다.
 *
 * <p><b>바뀐 것은 반환 타입뿐이다.</b> 예전에는 {@code presentation/dto/DetailResponse} 를
 * 직접 조립해 <b>infrastructure → presentation</b> 역방향 의존을 만들었다.
 * 이제 요약문만 돌려주고 응답 조립은 {@code presentation/AiConverter} 가 한다.
 * 프롬프트 문자열·web_search_preview 도구 설정·마크다운 정리 정규식은 그대로다.
 *
 * <p><b>폴백 2경로 — As-Is 그대로 유지한다.</b> {@code null} 을 반환해
 * 상세 응답은 정상적으로 내려가고 {@code aiSummary} 만 비게 한다.
 * <ol>
 *   <li>{@code JsonProcessingException}</li>
 *   <li>{@code DomainException} (OpenAI 호출 실패)</li>
 * </ol>
 * (추천 쪽과 달리 {@code extractJson} 을 쓰지 않으므로 세 번째 경로가 없다 — As-Is 와 동일.)
 *
 * <p>빈 이름을 {@code detailAiSummaryService} 로 고정한다.
 */
@Component("detailAiSummaryService")
@Slf4j
public class OpenAiRegionSummaryApiAdapter implements RegionSummaryProvider {
    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;
    private final String MODEL;
    private final Double TEMPERATURE;

    public OpenAiRegionSummaryApiAdapter(OpenAiClient openAiClient, ObjectMapper objectMapper,
                                  @Value("${openai.model}") String model,
                                  @Value("${openai.temperature}") Double temperature) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
        this.MODEL = model;
        this.TEMPERATURE = temperature;
    }

    @Override
    public String summarize(RegionDetailInfo dto){
        try{
            String json = objectMapper.writeValueAsString(dto);
            Tool extractedTool = extractedTool();

            OpenAiMessage system = new OpenAiMessage(
                    "system",
                            "모든 질문에는 친절한 AI 비서로서 답변해주세요." +
                            "응답은 한국어로, 간결하고 사실 기반으로 작성하세요.",
                    List.of(extractedTool)
            );

            String userPrompt = """
                아래는 특정 지역의 상세 데이터(JSON)입니다.
                이 정보를 바탕으로 사람이 읽기 쉬운 자연스러운 한국어 요약을 작성하세요.
                이미 제공된 페이지에 구체적인 수치가 있으므로, 구체적인 수치를 표현할 필요는 없습니다.
                사용자가 해당 지역의 매력을 느낄 수 있도록, 해당 지역 ‘고유 특색/장점’을 포함하되,
                이는 반드시 웹 검색 결과(신뢰 가능한 출처 2개 이상)에 기반해야 한다.
                만약 검색 결과가 부족하거나 신뢰할 만한 정보가 없을 경우, ‘근거 부족’과 같은 문구를 사용하지 말고,
                일반적인 지역의 긍정적 특징이나 분위기 중심으로 자연스럽게 서술하세요.
                
                도구 사용 지시:
                - web_search_preview 도구를 사용해 지역명과 함께 다음 범주를 우선 조회하세요: 축제/행사, 문화·자연 명소, 특산물/지역산업, 대학/연구기관, 공공 인프라나 도시정책의 차별점.
                - 서로 다른 매체의 최근 기사 또는 공신력 있는 기관 자료를 최소 2건 확보하세요. 일치하거나 상호 보완되는 사실만 채택하세요.
                - 모호하거나 상충되는 정보는 제외하고, 근거가 부족하면 ‘근거 부족으로 일반적 특성만 제공’ 원칙을 적용해 고유 특색/장점 문장을 쓰지 마세요.

                작성 규칙:
                1) 마크다운 문법은 절대 사용하지 마세요.
                   - 예: ```json, ```, `, *, -, +, #, |, [], (), **굵게**, *기울임*, [텍스트](링크), 1. 2. 3. 등
                   - 마크다운 줄바꿈(두 칸 공백 후 개행: '  \\n')도 금지합니다. 일반 개행(\\n)만 사용하세요.
                2) 출력은 오직 순수 텍스트입니다. 코드블록, 리스트, 표, 인용구, 링크, 헤딩 등은 포함하지 말고, 구어체로 작성하세요.
                3) 과장/추정/감정 표현 금지. 데이터가 없는 사실은 배제하세요.
                4) 상대점수·비율 등은 '상대 평가'임을 반드시 명시하세요.

                입력 JSON 데이터:
                %s
                """.formatted(json);

            OpenAiMessage user = new OpenAiMessage("user", userPrompt,List.of(extractedTool));
            OpenAiRequest request = new OpenAiRequest(MODEL, List.of(system, user), TEMPERATURE);

            OpenAiResponse response = openAiClient.getChatCompletion(request);

            String aiSummaryContent = response.getChoices().get(0).getMessage().getContent()
                    .replaceAll(" {2,}\\n", "\n") // 공백 2개+개행 → 일반 개행
                    .replaceAll("[ \t]+\\r?\\n", "\n") // 줄 끝 공백 제거
                    .trim();

            return aiSummaryContent;
        } catch (JsonProcessingException e) {
            return null;                    // 폴백 ① — As-Is 의 toResponseDTO(dto, null) 과 같은 결과
        } catch (DomainException e){
            log.warn("OpenAI API 호출 실패");
            return null;                    // 폴백 ②
        }
    }

    private static Tool extractedTool() {
        UserLocation location = UserLocation.builder()
                .type("approximate")
                .country("KR")
                .city("Seoul")
                .region("Seoul")
                .build();

        return Tool.builder()
                .type("web_search_preview")
                .userLocation(location)
                .build();
    }
}
