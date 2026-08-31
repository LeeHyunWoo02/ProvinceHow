package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 사람인 채용 목록 링크 어댑터 테스트. 우리 시군구 코드가 URL 로 새어나가지 않는지가 핵심이다.
 */
class SaraminJobListingLinkAdapterTest {

    private static final String BASE_URL = "https://www.saramin.co.kr";
    private static final String PATH = "/zf_user/jobs/list/domestic";

    /** 실측 확인값: 목포시 12110 ↔ 사람인 112080 */
    private static final Map<String, String> MOKPO = Map.of("112080", "12110");

    @Test
    @DisplayName("역매핑이 있으면 loc_cd 에 사람인 코드가 들어간다")
    void usesSaraminLocCodeWhenReverseMappingPresent() {
        SaraminJobListingLinkAdapter adapter = adapter(MOKPO);

        String link = adapter.linkFor(SigunguCode.of("12110"));

        assertThat(link).isEqualTo(BASE_URL + PATH + "?loc_cd=112080");
    }

    @Test
    @DisplayName("역매핑이 없으면 지역 파라미터 없이 전국 목록 링크를 만든다")
    void omitsRegionParamWhenReverseMappingAbsent() {
        SaraminJobListingLinkAdapter adapter = adapter(MOKPO);

        String link = adapter.linkFor(SigunguCode.of("11680"));

        assertThat(link).isEqualTo(BASE_URL + PATH);
        assertThat(link).doesNotContain("loc_cd");
    }

    @Test
    @DisplayName("우리 시군구 코드가 URL 에 그대로 실리지 않는다")
    void neverLeaksOurSigunguCode() {
        SaraminJobListingLinkAdapter adapter = adapter(MOKPO);

        assertThat(adapter.linkFor(SigunguCode.of("12110"))).doesNotContain("=12110");
        assertThat(adapter.linkFor(SigunguCode.of("11680"))).doesNotContain("11680");
    }

    @Test
    @DisplayName("직종을 넘겨도 job_cd 를 붙이지 않고 지역만 필터한다")
    void doesNotAppendJobParam() {
        SaraminJobListingLinkAdapter adapter = adapter(MOKPO);

        String link = adapter.linkFor(SigunguCode.of("12110"), JobCode.of("133"));

        assertThat(link).isEqualTo(BASE_URL + PATH + "?loc_cd=112080");
        assertThat(link).doesNotContain("job_cd");
    }

    @Test
    @DisplayName("역매핑이 없는 지역에 직종을 넘기면 파라미터 없는 전국 목록 링크가 된다")
    void returnsPlainListingWhenNeitherRegionNorJobCanBeMapped() {
        SaraminJobListingLinkAdapter adapter = adapter(MOKPO);

        String link = adapter.linkFor(SigunguCode.of("11680"), JobCode.of("133"));

        assertThat(link).isEqualTo(BASE_URL + PATH);
    }

    private SaraminJobListingLinkAdapter adapter(Map<String, String> saraminToOurs) {
        SaraminApiSpecFile spec = new SaraminApiSpecFile(null, null,
                new SaraminApiSpecFile.Mapping(false, saraminToOurs, false, Map.of(), Set.of()));
        return new SaraminJobListingLinkAdapter(
                new SaraminLocCodeResolver(new FixedSpecLoader(spec)),
                BASE_URL, PATH, "loc_cd", "job_cd");
    }

    /** 파일을 읽지 않고 주어진 스펙만 돌려주는 로더. */
    private static final class FixedSpecLoader extends SaraminApiSpecLoader {
        private final SaraminApiSpecFile fixed;

        private FixedSpecLoader(SaraminApiSpecFile fixed) {
            super(new ObjectMapper(), new DefaultResourceLoader(), "classpath:saramin/absent.json");
            this.fixed = fixed;
        }

        @Override
        public SaraminApiSpecFile spec() {
            return fixed;
        }
    }
}
