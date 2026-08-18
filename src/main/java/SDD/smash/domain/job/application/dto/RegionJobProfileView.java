package SDD.smash.domain.job.application.dto;

import SDD.smash.domain.job.domain.model.RegionJobProfile;

import java.util.List;

/**
 * 지역 채용 프로필 조회 결과(유스케이스 출력). {@code job} 컨텍스트가 다른 컨텍스트에 넘기는 DTO다.
 * 도메인 모델({@link RegionJobProfile})을 그대로 노출하지 않는다.
 *
 * @param sigunguCode        시군구 코드
 * @param salaryMedianManwon 연봉 중앙값(만원). 없으면 {@code null}
 * @param newcomerRatio      신입 채용 비율(0~1). 없으면 {@code null}
 * @param topIndustries      업종 구성 상위 N
 * @param sampleSize         전체 표본 수
 * @param salaryParsedCount  연봉을 숫자로 파싱한 표본 수
 */
public record RegionJobProfileView(String sigunguCode,
                                   Integer salaryMedianManwon,
                                   Double newcomerRatio,
                                   List<IndustryShareView> topIndustries,
                                   int sampleSize,
                                   int salaryParsedCount) {

    public record IndustryShareView(String name, int count) {
    }

    public static RegionJobProfileView from(RegionJobProfile p) {
        return new RegionJobProfileView(
                p.region().value(),
                p.salaryMedianManwon(),
                p.newcomerRatio(),
                p.topIndustries().stream()
                        .map(s -> new IndustryShareView(s.name(), s.count()))
                        .toList(),
                p.sampleSize(),
                p.salaryParsedCount());
    }
}
