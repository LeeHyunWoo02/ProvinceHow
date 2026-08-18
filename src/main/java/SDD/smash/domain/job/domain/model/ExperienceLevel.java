package SDD.smash.domain.job.domain.model;

/**
 * 채용공고의 경력 구분. 사람인 {@code experience-level.code} 를 도메인 개념으로 옮긴 것이다.
 *
 * <p>코드 대응(사람인 확인값): 1=신입, 2=경력, 3=신입/경력, 0=경력무관. 그 외/미상은 {@link #UNKNOWN}.
 *
 * <p>"신입 채용 비율"의 분자·분모 판정 규칙을 이 enum이 소유한다(빈약한 모델 방지). 이 지표의 의미는
 * <b>"청년(신입)이 지원 가능한 공고의 비율"</b>이다 — 신입(1)·신입/경력(3)·경력무관(0)은 모두 신입이
 * 지원할 수 있으므로 분자에 포함하고, 순수 경력(2)만 제외한다.
 */
public enum ExperienceLevel {

    NEWCOMER,      // 1 신입
    EXPERIENCED,   // 2 경력
    BOTH,          // 3 신입/경력
    ANY,           // 0 경력무관
    UNKNOWN;       // 미상/파싱 불가

    public static ExperienceLevel fromCode(Integer code) {
        if (code == null) {
            return UNKNOWN;
        }
        return switch (code) {
            case 1 -> NEWCOMER;
            case 2 -> EXPERIENCED;
            case 3 -> BOTH;
            case 0 -> ANY;
            default -> UNKNOWN;
        };
    }

    /** 경력 구분이 확인된 표본인가(신입 비율의 분모에 들어가는가). {@link #UNKNOWN} 만 제외한다. */
    public boolean isKnown() {
        return this != UNKNOWN;
    }

    /**
     * 신입이 지원 가능한가(신입 비율의 분자). 신입(1)·신입/경력(3)·경력무관(0)이 참이다.
     * 순수 경력(2)만 거짓이다 — 지표가 "청년(신입)이 지원 가능한 공고 비율"을 뜻하기 때문이다.
     */
    public boolean isNewcomerFriendly() {
        return this == NEWCOMER || this == BOTH || this == ANY;
    }
}
