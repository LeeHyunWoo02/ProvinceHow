package SDD.smash.global.util;

import java.util.Optional;

/**
 * 외부 API 재시도의 순수 계산 유틸. 지수 백오프 지연과 Retry-After 파싱만 담당한다.
 * 재시도 대상 판정(isRetryable)과 실제 sleep(인터럽트 처리)은 서버마다 달라 각 어댑터에 남긴다.
 */
public final class RetryBackoff {

    private RetryBackoff() {
    }

    /**
     * 지수 백오프 지연. {@code baseDelayMs * multiplier^(attempt-1)} 를 {@code maxDelayMs} 로 자른다.
     * attempt 는 1부터다. 실제로 sleep 하지 않는 순수 계산이다.
     */
    public static long backoffDelayMs(long baseDelayMs, double multiplier, int attempt, long maxDelayMs) {
        if (baseDelayMs <= 0) {
            return 0;
        }
        int exponent = Math.max(0, attempt - 1);
        double delay = baseDelayMs * Math.pow(Math.max(1.0d, multiplier), exponent);
        if (Double.isNaN(delay) || delay >= maxDelayMs) {
            return Math.max(0, maxDelayMs);
        }
        return Math.min(Math.max(0, maxDelayMs), (long) delay);
    }

    /**
     * Retry-After 헤더 값을 밀리초로 파싱한다. 초 단위 정수만 인정한다.
     * 값이 없거나(공백 포함) 숫자가 아니면(HTTP-date 등) {@code empty} 를 돌려 기본 지연으로 폴백하게 한다.
     */
    public static Optional<Long> retryAfterMillis(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.parseLong(headerValue.trim()) * 1000L);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
