package SDD.smash.domain.address.infrastructure.external;

import SDD.smash.domain.address.domain.model.PopulationSnapshot;
import SDD.smash.domain.address.infrastructure.external.dto.KosisPopulationRow;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static SDD.smash.global.util.BatchTextUtil.digitsOnly;
import static SDD.smash.global.util.BatchTextUtil.isBlank;
import static SDD.smash.global.util.MapperUtil.text;

/**
 * KOSIS 응답 JSON → 기술 DTO → 도메인 {@code PopulationSnapshot} 변환.
 *
 * <p>여기서 하는 정제는 둘이다.
 * <ul>
 *   <li><b>시군구 코드 5자리 정규화</b> — 콤마·BOM·제로폭 문자를 털고 숫자만 남긴다.
 *       그 결과가 5자리가 아니면 시군구 행이 아니다(전국 {@code 00}, 시도 2자리, 읍면동 7자리 이상).</li>
 *   <li><b>수치 정제</b> — {@code "1,234"} 같은 표기를 벗기고, 통계부호({@code -} 등)는 변환 실패로 본다.</li>
 * </ul>
 *
 * <p><b>자릿수만으로 시군구를 확정하지 않는다.</b> 5자리를 통과해도 {@code sigungu} 테이블에
 * 실제로 있는 코드인지는 애플리케이션 계층이 다시 대조한다.
 */
final class KosisPopulationJsonMapper {

    private static final DateTimeFormatter PRD_DE_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");
    private static final int SIGUNGU_CODE_LENGTH = 5;

    private KosisPopulationJsonMapper() {
    }

    /** 응답 배열의 한 원소를 기술 DTO 로 옮긴다. 필드가 없으면 {@code null} 이 들어간다. */
    static KosisPopulationRow toRow(JsonNode node) {
        return new KosisPopulationRow(
                text(node, "C1"),
                text(node, "C1_NM"),
                text(node, "ITM_ID"),
                text(node, "PRD_DE"),
                text(node, "DT"));
    }

    /**
     * 시군구 인구 한 건으로 승격한다.
     *
     * <p>시군구 행이 아니거나(전국/시도/읍면동), 수치·기준월을 읽을 수 없으면 {@code Optional.empty()} 다.
     * 예외를 던지지 않는다 — 한 행 때문에 배치 전체가 죽으면 안 된다.
     */
    static Optional<PopulationSnapshot> toSnapshot(KosisPopulationRow row) {
        if (row == null) return Optional.empty();

        String code = normalizeSigunguCode(row.c1());
        if (code == null) return Optional.empty();

        Integer count = parseCount(row.dt());
        if (count == null) return Optional.empty();

        YearMonth month = parseMonth(row.prdDe());
        if (month == null) return Optional.empty();

        try {
            return Optional.of(PopulationSnapshot.of(SigunguCode.of(code), count, month));
        } catch (DomainException e) {
            return Optional.empty();
        }
    }

    /** 숫자만 남긴 뒤 5자리일 때만 시군구 코드로 인정한다. 아니면 {@code null}. */
    static String normalizeSigunguCode(String raw) {
        if (isBlank(raw)) return null;
        String digits = digitsOnly(raw);
        if (digits.length() != SIGUNGU_CODE_LENGTH) return null;
        if (!digits.chars().allMatch(Character::isDigit)) return null;
        return digits;
    }

    /** {@code "1,234"} → 1234. 통계부호나 빈 값이면 {@code null}. */
    static Integer parseCount(String raw) {
        if (isBlank(raw)) return null;
        String digits = digitsOnly(raw);
        try {
            int value = Integer.parseInt(digits);
            return (value < 0) ? null : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** {@code "202606"} → 2026-06. 월 주기가 아니면 {@code null}. */
    static YearMonth parseMonth(String raw) {
        if (isBlank(raw)) return null;
        String digits = digitsOnly(raw);
        if (digits.length() != 6) return null;
        try {
            return YearMonth.parse(digits, PRD_DE_FORMAT);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
