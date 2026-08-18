package SDD.smash.domain.job.domain.service;

import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.job.domain.model.IndustryShare;
import SDD.smash.domain.job.domain.model.JobPostingSample;
import SDD.smash.domain.job.domain.model.RegionJobProfile;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

/**
 * 표본 공고들로 지역 채용 프로필 3개 지표를 계산하는 도메인 정책. <b>순수 함수</b>다
 * (저장소·캐시·시간·랜덤 의존 없음). 집계 규칙이 어댑터가 아니라 여기 있다(빈약한 모델 방지).
 *
 * <h2>지표 정의</h2>
 * <ul>
 *   <li><b>연봉 중앙값</b>: 각 표본의 연봉 대표값({@link JobPostingSample#salaryMidpointManwon()},
 *       구간의 중간값)들을 정렬한 중앙값. 표본 수가 짝수면 가운데 두 값의 평균(반올림).
 *       파싱 가능한 표본이 하나도 없으면 {@code null}.</li>
 *   <li><b>신입 채용 비율</b>("청년이 지원 가능한 공고 비율"): 분모 = 경력 구분이 확인된 표본 수
 *       ({@code experienceLevel.isKnown()}, UNKNOWN 제외), 분자 = 신입 지원 가능 표본 수
 *       ({@code isNewcomerFriendly()} = 신입·신입/경력·경력무관). 순수 경력(EXPERIENCED)만 분자에서
 *       빠진다. 분모가 0이면 {@code null}.</li>
 *   <li><b>업종 Top N</b>: 업종명 빈도 상위 N. 동률이면 업종명 오름차순으로 안정 정렬.</li>
 * </ul>
 */
public class RegionJobProfilePolicy {

    public RegionJobProfile profile(SigunguCode region, List<JobPostingSample> samples, int topN) {
        if (samples == null || samples.isEmpty()) {
            return RegionJobProfile.empty(region);
        }

        int[] midpoints = samples.stream()
                .map(JobPostingSample::salaryMidpointManwon)
                .filter(OptionalInt::isPresent)
                .mapToInt(OptionalInt::getAsInt)
                .sorted()
                .toArray();
        Integer salaryMedian = (midpoints.length == 0) ? null : median(midpoints);

        long known = samples.stream().filter(s -> s.experienceLevel().isKnown()).count();
        Double newcomerRatio = (known == 0) ? null
                : (double) samples.stream().filter(s -> s.experienceLevel().isNewcomerFriendly()).count() / known;

        Map<String, Long> counts = samples.stream()
                .filter(JobPostingSample::hasIndustry)
                .collect(groupingBy(s -> s.industryName().trim(), counting()));
        List<IndustryShare> topIndustries = counts.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Long>>comparingLong(Map.Entry::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .limit(Math.max(0, topN))
                .map(e -> new IndustryShare(e.getKey(), e.getValue().intValue()))
                .toList();

        return new RegionJobProfile(region, salaryMedian, newcomerRatio, topIndustries,
                samples.size(), midpoints.length);
    }

    private int median(int[] sorted) {
        int n = sorted.length;
        int mid = n / 2;
        if (n % 2 == 1) {
            return sorted[mid];
        }
        return (int) Math.round((sorted[mid - 1] + sorted[mid]) / 2.0);
    }
}
