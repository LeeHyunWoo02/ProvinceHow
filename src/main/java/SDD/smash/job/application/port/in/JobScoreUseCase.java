package SDD.smash.job.application.port.in;

import SDD.smash.common.domain.model.Score;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.job.domain.model.JobCode;

import java.util.Map;

/** 일자리 적합도 점수 in-port. */
public interface JobScoreUseCase {

    /**
     * 전 시군구의 일자리 적합도.
     *
     * @param jobCode {@code null} 이면 시군구별 전체 일자리 수 기준,
     *                아니면 해당 중분류 일자리 수 기준
     */
    Map<SigunguCode, Score> scoresFor(JobCode jobCode);
}
