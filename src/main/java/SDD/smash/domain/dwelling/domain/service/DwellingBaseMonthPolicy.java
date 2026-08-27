package SDD.smash.domain.dwelling.domain.service;

import SDD.smash.domain.dwelling.domain.model.AggregationPeriod;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 전월세 실거래 집계의 <b>기준월</b>을 정하는 규칙.
 *
 * <p>"어느 달까지를 믿을 수 있는가"는 기술이 아니라 주거비 도메인의 지식이다. 근거는 두 가지다.
 * <ul>
 *   <li>국토부는 실거래를 "실시간 취합 후 익일 공개"한다 — 즉 <em>신고된 것</em>만 보인다.</li>
 *   <li>주택 임대차 계약 신고 기한이 <b>계약 체결일부터 30일</b>이다. 따라서 어떤 달의 자료는
 *       그 달이 끝나고 30일이 더 지나야 대체로 채워진다.</li>
 * </ul>
 * 두 사실을 합치면 <b>현재월에서 2개월 전</b>이 안전한 기준월이다. 실측(종로구 2026-08-13 조회)에서도
 * 당월 건수가 평월의 약 1/5 수준이었다. 다만 이 지연 폭은 기관이 문서로 확정한 값이 아니므로
 * 상수로 박지 않고 호출자가 넘기게 한다.
 *
 * <p><b>순수 함수다.</b> 현재 시각을 스스로 읽지 않고 {@code currentMonth} 를 인자로 받는다.
 * 시계는 application 의 관심사이며(backend-conventions §3) 그래야 이 규칙을 시계 없이 검증할 수 있다.
 */
public class DwellingBaseMonthPolicy {

    /** 신고 기한 30일 + 익일 공개를 감안한 기본 지연 개월 수. */
    public static final int DEFAULT_CONFIRMED_LAG_MONTHS = 2;

    /** 시세 산출에 쓰는 기본 집계 개월 수. 운영 기본값({@code dwelling.months})과 같게 유지한다. */
    public static final int DEFAULT_LOOKBACK_MONTHS = 10;

    /** 자동 계산이 확정월을 찾지 못했을 때 뒤로 물러날 수 있는 최대 개월 수. */
    public static final int DEFAULT_MAX_FALLBACK_MONTHS = 3;

    private static final int MIN_LAG_MONTHS = 0;
    private static final int MAX_LAG_MONTHS = 12;

    private static final DateTimeFormatter BASE_MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int BASE_MONTH_LENGTH = 6;

    /**
     * 확정된 것으로 볼 수 있는 기준월.
     *
     * @param currentMonth 지금이 속한 연월(Asia/Seoul 기준). application 이 시계에서 구해 넘긴다
     * @param lagMonths    현재월에서 거슬러 올라갈 개월 수. 0 이면 당월을 그대로 쓴다
     */
    public YearMonth confirmedBaseMonth(YearMonth currentMonth, int lagMonths) {
        if (currentMonth == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "현재 연월은 필수입니다.");
        }
        if (lagMonths < MIN_LAG_MONTHS || lagMonths > MAX_LAG_MONTHS) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH,
                    "확정 지연 개월 수는 " + MIN_LAG_MONTHS + "~" + MAX_LAG_MONTHS + " 범위여야 합니다.");
        }
        return currentMonth.minusMonths(lagMonths);
    }

    /**
     * 기준월 후보를 최신 순으로 만든다. 첫 번째가 규칙상 기준월이고, 그 달의 자료가
     * "확정 0건"이면 다음 후보(직전 월)로 물러난다.
     *
     * <p>후보를 무한히 늘리지 않는다. 계속 비어 있다면 그것은 시점 문제가 아니라
     * 그 지역에 실거래가 없거나 코드가 잘못된 것이므로, 뒤로 더 가도 달라지지 않는다.
     *
     * @param maxFallbackMonths 규칙상 기준월 뒤로 추가로 시도할 개월 수. 0 이면 후보는 1개다
     */
    public List<YearMonth> baseMonthCandidates(YearMonth currentMonth, int lagMonths, int maxFallbackMonths) {
        YearMonth primary = confirmedBaseMonth(currentMonth, lagMonths);
        if (maxFallbackMonths < 0) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "fallback 개월 수는 음수일 수 없습니다.");
        }
        List<YearMonth> candidates = new ArrayList<>(maxFallbackMonths + 1);
        for (int step = 0; step <= maxFallbackMonths; step++) {
            candidates.add(primary.minusMonths(step));
        }
        return List.copyOf(candidates);
    }

    /** 기준월에서 과거로 {@code lookbackMonths} 개월(기준월 포함)의 집계 구간. */
    public AggregationPeriod aggregationPeriod(YearMonth baseMonth, int lookbackMonths) {
        return AggregationPeriod.endingAt(baseMonth, lookbackMonths);
    }

    /**
     * 수동 지정 기준월 문자열({@code yyyyMM})을 해석한다.
     *
     * @return 비어 있으면 {@code null} — "지정하지 않았다"는 뜻이며 자동 계산으로 넘어간다
     * @throws DomainException 값이 있는데 형식이 틀린 경우. 조용히 자동 계산으로 넘어가면
     *                         운영자가 오타를 낸 사실이 묻힌다
     */
    public YearMonth parseOverride(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() != BASE_MONTH_LENGTH) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH,
                    "기준월은 yyyyMM 6자리여야 합니다.");
        }
        try {
            return YearMonth.parse(trimmed, BASE_MONTH_FORMAT);
        } catch (DateTimeParseException e) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "기준월 형식이 올바르지 않습니다.");
        }
    }

    /** 배치 파라미터·외부 API 가 쓰는 {@code yyyyMM} 표기. */
    public String format(YearMonth baseMonth) {
        if (baseMonth == null) {
            throw new DomainException(ErrorCode.NOT_FOUND_YEARMONTH, "기준월은 필수입니다.");
        }
        return baseMonth.format(BASE_MONTH_FORMAT);
    }
}
