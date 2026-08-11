package SDD.smash.address.domain.model;

import SDD.smash.common.domain.model.SidoCode;
import SDD.smash.common.exception.DomainException;
import SDD.smash.common.exception.ErrorCode;

/**
 * 시도 (Aggregate Root).
 *
 * <p>식별자는 {@link SidoCode} 다. 하위 시군구 목록을 객체로 물지 않는다.
 * 시군구는 별도 Aggregate 이며 {@code Sigungu.sidoCode()} 로 자신이 속한 시도를 가리킨다.
 */
public class Sido {

    private final SidoCode code;
    private final String name;

    private Sido(SidoCode code, String name) {
        if (code == null) {
            throw new DomainException(ErrorCode.ADDRESS_CODE_NOT_FOUND, "시도 코드는 필수입니다.");
        }
        this.code = code;
        this.name = name;
    }

    /** 저장소에서 복원할 때 쓴다. */
    public static Sido reconstitute(SidoCode code, String name) {
        return new Sido(code, name);
    }

    public SidoCode code() {
        return code;
    }

    public String name() {
        return name;
    }
}
