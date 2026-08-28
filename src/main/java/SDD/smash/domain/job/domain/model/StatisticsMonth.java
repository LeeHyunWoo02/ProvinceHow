package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

/**
 * 고용행정통계의 기준월. 표기는 {@code YYYY-MM} 이다.
 *
 * <p>화면이 "2026년 7월 기준" 을 반드시 붙여야 하므로 기준월은 부가 정보가 아니라
 * 통계값의 일부다. 그래서 값 객체로 못 박고 키({@link RegionJobStatisticsKey})에 넣는다.
 */
public record StatisticsMonth(YearMonth value) implements Comparable<StatisticsMonth> {

    /**
     * {@code YearMonth.parse} 만으로는 {@code 2026-7} 같은 자릿수 미달을 막지 못한다.
     * 자릿수를 먼저 고정한 뒤 파싱한다.
     */
    private static final Pattern FORMAT = Pattern.compile("[0-9]{4}-[0-9]{2}");

    public StatisticsMonth {
        if (value == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "통계 기준월은 필수입니다.");
        }
    }

    public static StatisticsMonth of(YearMonth value) {
        return new StatisticsMonth(value);
    }

    /** {@code YYYY-MM} 문자열을 기준월로 바꾼다. 형식이 어긋나면 {@link DomainException}. */
    public static StatisticsMonth of(String text) {
        if (text == null || !FORMAT.matcher(text.trim()).matches()) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH,
                    "통계 기준월은 YYYY-MM 형식이어야 합니다: " + text);
        }
        try {
            return new StatisticsMonth(YearMonth.parse(text.trim()));
        } catch (DateTimeParseException e) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH,
                    "유효하지 않은 통계 기준월입니다: " + text);
        }
    }

    /** 저장·표기에 쓰는 정본 문자열. 사전순 정렬이 곧 시간순이다. */
    public String text() {
        return value.toString();
    }

    @Override
    public int compareTo(StatisticsMonth other) {
        return value.compareTo(other.value);
    }
}
