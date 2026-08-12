package SDD.smash.domain.support.application.port.in;

import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;

import java.util.Map;

/** 지원정책 적합도 점수 in-port. */
public interface SupportScoreUseCase {

    /**
     * 전 시군구의 지원정책 적합도.
     *
     * @param supportChoice {@code null} 이거나 아무 태그도 고르지 않았으면 빈 맵을 돌려준다
     *                       (호출부가 0점으로 처리한다)
     */
    Map<SigunguCode, Score> scoresFor(Integer supportChoice);
}
