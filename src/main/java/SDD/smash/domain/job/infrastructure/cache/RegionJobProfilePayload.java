package SDD.smash.domain.job.infrastructure.cache;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 지역 채용 프로필 캐시 페이로드. Jackson 역직렬화용이라 기본 생성자 + setter 를 갖는다.
 * {@code infrastructure/cache} 밖으로 나가지 않는다. 지역 코드는 키에 있으므로 담지 않는다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RegionJobProfilePayload {

    private Integer salaryMedianManwon;
    private Double newcomerRatio;
    private List<IndustrySharePayload> topIndustries;
    private int sampleSize;
    private int salaryParsedCount;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IndustrySharePayload {
        private String name;
        private int count;
    }
}
