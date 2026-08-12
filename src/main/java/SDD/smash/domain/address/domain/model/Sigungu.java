package SDD.smash.domain.address.domain.model;

import SDD.smash.global.domain.model.SidoCode;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.util.Optional;

/**
 * 시군구 (Aggregate Root). {@link Population} 을 포함한다.
 *
 * <p>상위 시도는 다른 Aggregate 이므로 객체가 아니라 {@link SidoCode} 로만 참조한다.
 * 인구는 아직 적재되지 않았을 수 있어 없을 수도 있다.
 */
public class Sigungu {

    private final SigunguCode code;
    private final String name;
    private final SidoCode sidoCode;
    private final Population population;

    private Sigungu(SigunguCode code, String name, SidoCode sidoCode, Population population) {
        if (code == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시군구 코드는 필수입니다.");
        }
        if (sidoCode == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시도 코드는 필수입니다.");
        }
        this.code = code;
        this.name = name;
        this.sidoCode = sidoCode;
        this.population = population;
    }

    /** 인구를 함께 싣지 않고 복원한다. 코드·이름만 필요한 조회 경로에서 쓴다. */
    public static Sigungu reconstitute(SigunguCode code, String name, SidoCode sidoCode) {
        return new Sigungu(code, name, sidoCode, null);
    }

    /** 인구까지 함께 복원한다. */
    public static Sigungu reconstitute(SigunguCode code, String name, SidoCode sidoCode, Population population) {
        return new Sigungu(code, name, sidoCode, population);
    }

    public SigunguCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public SidoCode sidoCode() {
        return sidoCode;
    }

    /** 인구 데이터가 적재되지 않았으면 비어 있다. */
    public Optional<Population> population() {
        return Optional.ofNullable(population);
    }
}
