package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * {@code infra.ratio} 한 행의 값 — 시군구 안에서 이 업종이 차지하는 비중.
 *
 * <h2>단위는 {@link RatioBasis} 가 정한다</h2>
 * 코드베이스만으로는 이 컬럼이 0~1 비율인지 0~100 퍼센트인지 <b>판별할 수 없었다</b>
 * ({@code GET /api/detail} 로 pass-through 될 뿐 산술·비교·포맷팅하는 코드가 없다).
 * 그래서 해석을 고정하지 않고 {@link RatioBasis} 로 전환 가능하게 두었고,
 * 기본값은 {@link RatioBasis#PERCENT}(0~100)다 — 근거는 {@code docs/localdata-infra.md} 참고.
 *
 * <p>여기서는 단위와 무관한 불변식만 강제한다: <b>음수가 아니다.</b>
 * 개수의 비중이므로 음수가 될 수 없고, 음수가 들어오면 계산이 잘못된 것이다.
 *
 * <p>{@code scale = 2}, {@code HALF_UP}. DB 컬럼은 {@code decimal(18,2)} 다.
 */
public record InfraRatio(BigDecimal value) {

    public static final int SCALE = 2;

    public InfraRatio {
        if (value == null) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인프라 비중은 필수입니다.");
        }
        value = value.setScale(SCALE, RoundingMode.HALF_UP);
        if (value.signum() < 0) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인프라 비중은 0 이상이어야 합니다.");
        }
    }

    public static InfraRatio of(BigDecimal value) {
        return new InfraRatio(value);
    }

    public static InfraRatio of(double value) {
        if (!Double.isFinite(value)) {
            throw new DomainException(ErrorCode.VALIDATION_FAILED, "인프라 비중이 유한한 수가 아닙니다.");
        }
        return new InfraRatio(BigDecimal.valueOf(value));
    }

    public static InfraRatio zero() {
        return new InfraRatio(BigDecimal.ZERO);
    }
}
