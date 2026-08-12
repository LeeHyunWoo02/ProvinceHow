package SDD.smash.domain.dwelling.application.port.in;

import SDD.smash.global.domain.model.Money;
import SDD.smash.global.domain.model.Score;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.dwelling.domain.model.DwellingType;

import java.util.Map;

/** 주거 적합도 점수 in-port. */
public interface DwellingScoreUseCase {

    /** 전 시군구의 주거 적합도. 실거래가 없는 시군구도 0점으로 포함된다. */
    Map<SigunguCode, Score> scoresFor(DwellingType type, Money budget);
}
