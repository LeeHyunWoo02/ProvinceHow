package SDD.smash.domain.job.domain.service;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.ExperienceLevel;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.domain.model.RegionJobProfile;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RegionJobProfilePolicyTest {

    private final RegionJobProfilePolicy policy = new RegionJobProfilePolicy();
    private final SigunguCode region = SigunguCode.of("11680");

    private JobPostingSample salary(int min, int max) {
        return new JobPostingSample(min, max, ExperienceLevel.UNKNOWN, null);
    }

    @Test
    @DisplayName("연봉 중앙값 - 표본 수가 홀수면 가운데 값")
    void salaryMedianOdd() {
        // given - 대표값 3000, 4000, 5000 (구간 중간값)
        RegionJobProfile profile = policy.profile(region,
                List.of(salary(3000, 3000), salary(4000, 4000), salary(5000, 5000)), 5);

        // then
        assertThat(profile.salaryMedianManwon()).isEqualTo(4000);
        assertThat(profile.salaryParsedCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("연봉 중앙값 - 표본 수가 짝수면 가운데 두 값의 평균(반올림)")
    void salaryMedianEven() {
        // given - 구간 중간값 3000, 4000 → (3000+4000)/2 = 3500
        RegionJobProfile profile = policy.profile(region,
                List.of(salary(2000, 4000), salary(3000, 5000)), 5);

        // then - 각 표본 midpoint 3000, 4000 → 중앙값 3500
        assertThat(profile.salaryMedianManwon()).isEqualTo(3500);
    }

    @Test
    @DisplayName("연봉 파싱 불가 표본이 섞이면 파싱된 표본으로만 중앙값을 낸다")
    void salaryMedianIgnoresUnparsed() {
        // given - 파싱 가능 2건(3000,5000) + 파싱 불가 1건
        RegionJobProfile profile = policy.profile(region, List.of(
                salary(3000, 3000),
                new JobPostingSample(null, null, ExperienceLevel.UNKNOWN, null),
                salary(5000, 5000)), 5);

        // then - [3000,5000] 중앙값 4000, 표본 3 중 연봉 파싱 2
        assertThat(profile.salaryMedianManwon()).isEqualTo(4000);
        assertThat(profile.sampleSize()).isEqualTo(3);
        assertThat(profile.salaryParsedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("연봉 파싱 가능한 표본이 없으면 중앙값은 null")
    void salaryMedianNullWhenNoneParsed() {
        RegionJobProfile profile = policy.profile(region,
                List.of(new JobPostingSample(null, null, ExperienceLevel.NEWCOMER, "IT")), 5);

        assertThat(profile.salaryMedianManwon()).isNull();
    }

    @Test
    @DisplayName("신입 비율 - 분자는 신입/신입·경력/경력무관, 순수 경력만 제외")
    void newcomerRatio() {
        // given - NEWCOMER, BOTH, ANY(분자) / EXPERIENCED(분모만) / UNKNOWN(제외)
        RegionJobProfile profile = policy.profile(region, List.of(
                new JobPostingSample(null, null, ExperienceLevel.NEWCOMER, null),
                new JobPostingSample(null, null, ExperienceLevel.BOTH, null),
                new JobPostingSample(null, null, ExperienceLevel.EXPERIENCED, null),
                new JobPostingSample(null, null, ExperienceLevel.ANY, null),
                new JobPostingSample(null, null, ExperienceLevel.UNKNOWN, null)), 5);

        // then - 분모 4(신입+신입경력+경력+경력무관), 분자 3(신입+신입경력+경력무관) → 0.75
        assertThat(profile.newcomerRatio()).isCloseTo(0.75, within(1e-9));
    }

    @Test
    @DisplayName("신입 비율 - 경력무관만 있으면 1.0(청년 지원 가능)")
    void newcomerRatioAllAnyIsOne() {
        RegionJobProfile profile = policy.profile(region, List.of(
                new JobPostingSample(null, null, ExperienceLevel.ANY, null),
                new JobPostingSample(null, null, ExperienceLevel.ANY, null)), 5);

        assertThat(profile.newcomerRatio()).isCloseTo(1.0, within(1e-9));
    }

    @Test
    @DisplayName("신입 비율 - 경력 구분이 확인된 표본이 없으면 null")
    void newcomerRatioNullWhenNoneKnown() {
        RegionJobProfile profile = policy.profile(region,
                List.of(new JobPostingSample(3000, 4000, ExperienceLevel.UNKNOWN, "IT")), 5);

        assertThat(profile.newcomerRatio()).isNull();
    }

    @Test
    @DisplayName("업종 Top - 빈도 내림차순, 동률은 업종명 오름차순")
    void topIndustriesSortedByCountThenName() {
        // given - IT x3, 금융 x2, 건설 x2
        RegionJobProfile profile = policy.profile(region, List.of(
                industry("IT"), industry("IT"), industry("IT"),
                industry("금융"), industry("금융"),
                industry("건설"), industry("건설")), 2);

        // then - top2: IT(3), 그다음 동률(건설/금융) 중 이름 오름차순 → 건설
        assertThat(profile.topIndustries()).hasSize(2);
        assertThat(profile.topIndustries().get(0).name()).isEqualTo("IT");
        assertThat(profile.topIndustries().get(0).count()).isEqualTo(3);
        assertThat(profile.topIndustries().get(1).name()).isEqualTo("건설");
    }

    @Test
    @DisplayName("빈 표본이면 안전한 빈 프로필")
    void emptySamples() {
        RegionJobProfile profile = policy.profile(region, List.of(), 5);

        assertThat(profile.isEmpty()).isTrue();
        assertThat(profile.salaryMedianManwon()).isNull();
        assertThat(profile.newcomerRatio()).isNull();
        assertThat(profile.topIndustries()).isEmpty();
        assertThat(profile.sampleSize()).isZero();
    }

    private JobPostingSample industry(String name) {
        return new JobPostingSample(null, null, ExperienceLevel.UNKNOWN, name);
    }
}
