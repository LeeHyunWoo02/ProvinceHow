package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.domain.job.application.dto.RegionJobProfileView;

import java.util.List;

/**
 * 추천 지역 상세에 실리는 지역 채용 프로필(recommendation 로컬 요약 DTO).
 * job 컨텍스트의 {@link RegionJobProfileView} 를 recommendation 소유 타입으로 재포장한다
 * ({@code JobInfoSummary.from}, {@code JobVacancyItem.from} 과 같은 관례).
 *
 * @param salaryMedianManwon 연봉 중앙값(만원). 없으면 {@code null}
 * @param newcomerRatio      신입 채용 비율(0~1). 없으면 {@code null}
 * @param topIndustries      업종 구성 상위 N
 * @param sampleSize         전체 표본 수
 * @param salaryParsedCount  연봉을 숫자로 파싱한 표본 수
 */
public record RegionJobProfileItem(Integer salaryMedianManwon,
                                   Double newcomerRatio,
                                   List<IndustryShareItem> topIndustries,
                                   int sampleSize,
                                   int salaryParsedCount) {

    public record IndustryShareItem(String name, int count) {
    }

    public static RegionJobProfileItem from(RegionJobProfileView v) {
        if (v == null) {
            return null;
        }
        return new RegionJobProfileItem(
                v.salaryMedianManwon(),
                v.newcomerRatio(),
                v.topIndustries().stream()
                        .map(i -> new IndustryShareItem(i.name(), i.count()))
                        .toList(),
                v.sampleSize(),
                v.salaryParsedCount());
    }
}
