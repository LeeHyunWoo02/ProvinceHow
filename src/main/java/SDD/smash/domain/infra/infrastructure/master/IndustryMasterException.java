package SDD.smash.domain.infra.infrastructure.master;

/** 업종 마스터/지역코드 매핑 설정을 읽지 못했을 때의 기술 예외. */
public class IndustryMasterException extends RuntimeException {

    public IndustryMasterException(String message) {
        super(message);
    }

    public IndustryMasterException(String message, Throwable cause) {
        super(message, cause);
    }
}
