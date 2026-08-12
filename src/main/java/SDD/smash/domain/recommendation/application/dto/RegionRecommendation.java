package SDD.smash.domain.recommendation.application.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 지역 추천 결과 한 건. As-Is {@code RecommendDTO} 자리를 대신한다.
 *
 * <p><b>일반 record가 아니라 Lombok {@code @Getter @Builder} 클래스다.</b>
 * {@code OpenAI.Converter.AiConverter}/{@code AiRecommendService} 가 이 타입을
 * {@code getSidoCode()} 같은 자바빈 getter로 읽고 {@code .builder()...build()} 로
 * 다시 만든다({@code AiConverter.toResponseList} 참고) — OpenAI 내부를 재구조화하지
 * 말라는 지시 때문에 그 호출 형태를 그대로 받아줄 수 있는 모양을 유지해야 한다.
 *
 * <p>또한 {@code RecommendController} 가 {@code recommendRegionUseCase.recommend(...)} 의
 * 반환값을 가공 없이 그대로 {@code aiRecommendService.summarize(list)} 에 넘기므로,
 * 이 타입은 <b>유스케이스의 출력이자 최종 HTTP 응답({@code RecommendAggregateResponse.items})의
 * 원소 타입이기도 하다.</b> backend-conventions §6 예시(도메인→Response 매핑)와 다르게
 * 3-DTO 분리를 여기서는 완전히 지키지 못한다 — OpenAI 의존성이 강제한 의도적 타협이며
 * 보고서에 근거를 남긴다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegionRecommendation {

    private String sidoCode;
    private String sidoName;
    private String sigunguCode;
    private String sigunguName;
    private Integer score;

    private JobInfoSummary totalJobInfo;
    private JobInfoSummary fitJobInfo;

    private Integer totalSupportNum;
    private Integer fitSupportNum;

    private DwellingSimpleInfoSummary dwellingSimpleInfo;

    private List<MajorInfraSummaryItem> infraMajors;
}
