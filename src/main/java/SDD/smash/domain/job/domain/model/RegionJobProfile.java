package SDD.smash.domain.job.domain.model;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;

import java.util.List;

/**
 * 지역 채용 프로필(집계 결과). 표본 공고들로부터 계산한 3개 지표를 담는다.
 * 계산 규칙은 {@code RegionJobProfilePolicy} 가 소유하며, 이 타입은 그 산출물이다.
 *
 * @param region             대상 시군구
 * @param salaryMedianManwon 연봉 중앙값(만원). 파싱 가능한 표본이 없으면 {@code null}
 * @param newcomerRatio      신입 채용 비율(0~1). 경력 구분이 확인된 표본이 없으면 {@code null}
 * @param topIndustries      업종 구성 상위 N
 * @param sampleSize         집계에 쓴 전체 표본 수
 * @param salaryParsedCount  그 중 연봉을 숫자로 파싱한 표본 수(중앙값의 근거 수 — 정직성)
 */
public record RegionJobProfile(SigunguCode region,
                               Integer salaryMedianManwon,
                               Double newcomerRatio,
                               List<IndustryShare> topIndustries,
                               int sampleSize,
                               int salaryParsedCount) {

    public RegionJobProfile {
        if (region == null) {
            throw new DomainException(ErrorCode.JOB_PROFILE_INVALID, "프로필 지역은 필수입니다.");
        }
        topIndustries = (topIndustries == null) ? List.of() : List.copyOf(topIndustries);
    }

    /** 표본이 하나도 없어 의미 있는 프로필이 아님. 캐싱하지 않는 판정에 쓴다. */
    public boolean isEmpty() {
        return sampleSize == 0;
    }

    public static RegionJobProfile empty(SigunguCode region) {
        return new RegionJobProfile(region, null, null, List.of(), 0, 0);
    }
}
