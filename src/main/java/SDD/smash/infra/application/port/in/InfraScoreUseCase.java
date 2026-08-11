package SDD.smash.infra.application.port.in;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;

import java.util.Map;

/** 인프라 적합도 점수 in-port. */
public interface InfraScoreUseCase {

    /**
     * 전 시군구의 인프라 적합도.
     *
     * @param infraChoice 사용자가 고른 인프라 대분류의 비트마스크. {@code null} 이거나
     *                    아무 대분류도 고르지 않았으면 빈 맵을 돌려준다(호출부가 0점으로 처리한다)
     */
    Map<SigunguCode, Score> scoresFor(Integer infraChoice);
}
