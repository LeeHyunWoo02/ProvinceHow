package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.SaraminJobPostingRaw;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SaraminCodeMapperTest {

    private static final String SPEC_PATH = "classpath:saramin/saramin-job-api.json";

    @Test
    @DisplayName("배포 스펙: passthrough 꺼짐, 지역 264건·직종 21건 초안 채워짐, jobCodePath 는 job-mid-code")
    void shippedSpecHasRegionAndJobDrafts() {
        // given
        SaraminApiSpecLoader loader =
                new SaraminApiSpecLoader(new ObjectMapper(), new DefaultResourceLoader(), SPEC_PATH);

        // when
        SaraminApiSpecFile spec = loader.spec();

        // then
        assertThat(spec.request().accessKeyParam()).isEqualTo("access-key");
        assertThat(spec.request().maxCount()).isEqualTo(110);
        assertThat(spec.response().rootField()).isEqualTo("jobs");
        assertThat(spec.response().regionCodePath()).isEqualTo("position.location.code");
        // 직종 crosswalk 가 대분류 수준이라 job-mid-code 경로를 쓴다.
        assertThat(spec.response().jobCodePath()).isEqualTo("position.job-mid-code.code");
        assertThat(spec.mapping().regionCodePassthrough()).isFalse();
        assertThat(spec.mapping().jobCodePassthrough()).isFalse();
        // 지역 매핑표 초안(우리 시군구 264개 1:1).
        assertThat(spec.mapping().regionCodes())
                .hasSize(264)
                .containsEntry("101010", "11680")   // 서울 강남구
                .containsEntry("104090", "27720")   // 대구 군위군
                .containsEntry("118000", "36110");  // 세종전체 -> 세종
        // 직종 crosswalk 초안(사람인 대분류 mcode 21개 -> KECO 중분류).
        assertThat(spec.mapping().jobCodes())
                .hasSize(21)
                .containsEntry("2", "024")    // IT개발·데이터 -> 소프트웨어
                .containsEntry("16", "015")   // 기획·전략 -> 행정·경영·회계·광고·상품기획
                .containsEntry("3", "018")    // 회계·세무·재무 -> 회계·경리 사무
                .containsEntry("17", "01C");  // 금융·보험 -> 금융·보험 전문가
    }

    @Test
    @DisplayName("배포 스펙 매핑표로 실제 loc_cd 와 job-mid-code(mcode)를 우리 코드로 옮긴다")
    void mapsRealLocAndJobCodeWithShippedSpec() {
        // given - 실제 배포 스펙 로더를 그대로 쓴다
        SaraminApiSpecLoader loader =
                new SaraminApiSpecLoader(new ObjectMapper(), new DefaultResourceLoader(), SPEC_PATH);
        SaraminCodeMapper mapper = new SaraminCodeMapper(loader);

        // when - 사람인 강남구(101010) + IT개발·데이터 대분류(mcode 2)
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("46203390", "101010", "2")));

        // then - 지역 11680, 직종 024(소프트웨어)로 옮겨져 집계 대상이 된다
        assertThat(result.unresolvedRegionCount()).isZero();
        assertThat(result.unresolvedJobCount()).isZero();
        assertThat(result.postings()).hasSize(1);
        assertThat(result.postings().get(0).regions()).containsExactly(SigunguCode.of("11680"));
        assertThat(result.postings().get(0).jobCodes()).containsExactly(JobCode.of("024"));
    }

    @Test
    @DisplayName("매핑표가 비어 있으면 모든 공고가 지역 미해결로 버려지고 건수를 센다")
    void allPostingsUnresolvedWhenMappingEmpty() {
        // given - 배포 기본값: passthrough=false, 매핑표 비어 있음
        SaraminCodeMapper mapper = mapperWith(SaraminApiSpecFile.defaults());

        // when
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("46203390", "101000", "84"),
                new SaraminJobPostingRaw("46203391", "110000", "85")));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isEqualTo(2);
        assertThat(result.unresolvedJobCount()).isZero();
    }

    @Test
    @DisplayName("명시 매핑을 채우면 사람인 코드를 우리 코드로 옮긴다")
    void mapsSaraminCodesWhenExplicitMappingProvided() {
        // given
        SaraminCodeMapper mapper = mapperWith(specWith(new SaraminApiSpecFile.Mapping(
                false, Map.of("101000", "11680"), false, Map.of("84", "133"), Set.of())));

        // when
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("46203390", "101000", "84")));

        // then
        assertThat(result.postings()).hasSize(1);
        assertThat(result.postings().get(0).regions()).containsExactly(SigunguCode.of("11680"));
        assertThat(result.postings().get(0).jobCodes()).containsExactly(JobCode.of("133"));
    }

    @Test
    @DisplayName("지역만 옮기면 직종 미해결로 버리고 건수를 센다")
    void countsJobUnresolvedWhenOnlyRegionMapped() {
        // given
        SaraminCodeMapper mapper = mapperWith(specWith(new SaraminApiSpecFile.Mapping(
                false, Map.of("101000", "11680"), false, Map.of(), Set.of())));

        // when
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("46203390", "101000", "84")));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isZero();
        assertThat(result.unresolvedJobCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("ignoredRegionCodes 에 있는 코드는 조용히 버린다")
    void silentlyDropsIgnoredRegionCode() {
        // given
        SaraminCodeMapper mapper = mapperWith(specWith(new SaraminApiSpecFile.Mapping(
                false, Map.of(), false, Map.of(), Set.of("000000"))));

        // when
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("46203390", "000000", "84")));

        // then - ignored 는 unresolved 로 세지 않는다(지역이 비어 미해결이지만 경고 대상은 아니다)
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("매핑 결과가 시도 대표코드면 어느 시군구인지 몰라 버린다")
    void dropsSidoLevelMappedCode() {
        // given
        SaraminCodeMapper mapper = mapperWith(specWith(new SaraminApiSpecFile.Mapping(
                false, Map.of("110000", "11000"), false, Map.of("84", "133"), Set.of())));

        // when
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("46203391", "110000", "84")));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("공고 식별자가 없으면 건너뛴다")
    void skipsPostingWithoutId() {
        // given
        SaraminCodeMapper mapper = mapperWith(specWith(new SaraminApiSpecFile.Mapping(
                false, Map.of("101000", "11680"), false, Map.of("84", "133"), Set.of())));

        // when
        SaraminCodeMapper.PageMapping result = mapper.map(List.of(
                new SaraminJobPostingRaw("  ", "101000", "84")));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isZero();
    }

    private SaraminApiSpecFile specWith(SaraminApiSpecFile.Mapping mapping) {
        return new SaraminApiSpecFile(null, null, mapping);
    }

    private SaraminCodeMapper mapperWith(SaraminApiSpecFile spec) {
        return new SaraminCodeMapper(new FixedSpecLoader(spec));
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
