package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.RegionJobProfileItem;

import java.util.List;

/**
 * 지역 상세 응답에 실리는 지역 채용 프로필. HTTP 응답 계약(JSON 필드명)의 소유자다.
 * recommendation 로컬 요약({@link RegionJobProfileItem})을 표현 계층 타입으로 옮긴 것이다.
 *
 * @param salaryMedianManwon 연봉 중앙값(만원). 없으면 {@code null}
 * @param newcomerRatio      신입 채용 비율(0~1). 없으면 {@code null}
 * @param topIndustries      업종 구성 상위 N
 * @param sampleSize         전체 표본 수
 * @param salaryParsedCount  연봉을 숫자로 파싱한 표본 수
 */
public record RegionJobProfileEntry(Integer salaryMedianManwon,
                                    Double newcomerRatio,
                                    List<IndustryShareEntry> topIndustries,
                                    int sampleSize,
                                    int salaryParsedCount) {

    public record IndustryShareEntry(String name, int count) {
    }

    public static RegionJobProfileEntry from(RegionJobProfileItem item) {
        if (item == null) {
            return null;
        }
        return new RegionJobProfileEntry(
                item.salaryMedianManwon(),
                item.newcomerRatio(),
                item.topIndustries().stream()
                        .map(i -> new IndustryShareEntry(i.name(), i.count()))
                        .toList(),
                item.sampleSize(),
                item.salaryParsedCount());
    }
}
