package SDD.smash.domain.job.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

/**
 * 업종 구성의 한 항목. 지역 채용 프로필의 "업종 Top N"을 이루는 값 객체다.
 *
 * @param name  업종명(사람인 라벨 그대로)
 * @param count 이 업종의 표본 공고 수
 */
public record IndustryShare(String name, int count) {

    public IndustryShare {
        if (name == null || name.isBlank()) {
            throw new DomainException(ErrorCode.JOB_PROFILE_INVALID, "업종명은 필수입니다.");
        }
        if (count < 0) {
            throw new DomainException(ErrorCode.JOB_PROFILE_INVALID, "업종 건수는 0 이상이어야 합니다.");
        }
    }
}
