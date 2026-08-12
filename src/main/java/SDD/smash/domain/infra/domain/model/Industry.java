package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 업종 마스터 (Aggregate Root). As-Is {@code Industry} 엔티티에 해당한다.
 */
public class Industry {

    private final IndustryCode code;
    private final String name;
    private final Major major;

    private Industry(IndustryCode code, String name, Major major) {
        if (code == null) {
            throw new DomainException(ErrorCode.INDUSTRY_CODE_NOT_FOUND, "업종 코드는 필수입니다.");
        }
        this.code = code;
        this.name = name;
        this.major = major;
    }

    public static Industry reconstitute(IndustryCode code, String name, Major major) {
        return new Industry(code, name, major);
    }

    public IndustryCode code() {
        return code;
    }

    public String name() {
        return name;
    }

    public Major major() {
        return major;
    }
}
