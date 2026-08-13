package SDD.smash.domain.job.infrastructure.external;

import SDD.smash.domain.job.domain.model.JobCode;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetApiSpecFile;
import SDD.smash.domain.job.infrastructure.external.dto.WorknetJobPostingRaw;
import SDD.smash.global.domain.model.SigunguCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class WorknetCodeMapperTest {

    private static final String SPEC_PATH = "classpath:worknet/worknet-job-api.json";

    @Test
    @DisplayName("기본 설정에서는 워크넷 코드를 그대로 우리 코드로 쓴다")
    void passesCodesThroughByDefault() {
        // given
        WorknetCodeMapper mapper = mapperWith(defaultSpec());

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("11110"), List.of("011"))));

        // then
        assertThat(result.postings()).hasSize(1);
        assertThat(result.postings().get(0).regions()).containsExactly(SigunguCode.of("11110"));
        assertThat(result.postings().get(0).jobCodes()).containsExactly(JobCode.of("011"));
    }

    @Test
    @DisplayName("실제 배포되는 설정 파일이 그대로 로드된다")
    void loadsShippedSpecFile() {
        // given
        WorknetApiSpecLoader loader =
                new WorknetApiSpecLoader(new ObjectMapper(), new DefaultResourceLoader(), SPEC_PATH);

        // when
        WorknetApiSpecFile spec = loader.spec();

        // then
        assertThat(spec.request().authKeyParam()).isEqualTo("authKey");
        assertThat(spec.request().returnTypeValue()).isEqualTo("XML");
        assertThat(spec.request().maxDisplay()).isEqualTo(100);
        assertThat(spec.response().listElement()).isEqualTo("wanted");
        assertThat(spec.response().postingIdField()).isEqualTo("wantedAuthNo");
    }

    @Test
    @DisplayName("설정 파일이 없어도 기본값으로 떨어지고 기동을 막지 않는다")
    void fallsBackToDefaultsWhenSpecFileMissing() {
        // given
        WorknetApiSpecLoader loader = new WorknetApiSpecLoader(
                new ObjectMapper(), new DefaultResourceLoader(), "classpath:worknet/no-such-file.json");

        // when
        WorknetApiSpecFile spec = loader.spec();

        // then
        assertThat(spec.request().authKeyParam()).isEqualTo("authKey");
        assertThat(spec.mapping().regionCodePassthrough()).isTrue();
    }

    @Test
    @DisplayName("지역코드를 옮기지 못하면 임의 분류하지 않고 버린 뒤 건수를 센다")
    void dropsAndCountsPostingsWithUnmappableRegionCode() {
        // given - passthrough 를 끄고 매핑표를 비워 두면 어떤 지역코드도 옮길 수 없다
        WorknetCodeMapper mapper = mapperWith(specWith(new WorknetApiSpecFile.Mapping(
                false, Map.of(), true, Map.of(), Set.of())));

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("A0101"), List.of("011"))));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isEqualTo(1);
        assertThat(result.unresolvedJobCount()).isZero();
    }

    @Test
    @DisplayName("직종코드를 옮기지 못하면 버린 뒤 건수를 센다")
    void dropsAndCountsPostingsWithUnmappableJobCode() {
        // given
        WorknetCodeMapper mapper = mapperWith(specWith(new WorknetApiSpecFile.Mapping(
                true, Map.of(), false, Map.of(), Set.of())));

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("11110"), List.of("XYZ"))));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedJobCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("명시 매핑이 passthrough 보다 우선한다")
    void explicitMappingWinsOverPassthrough() {
        // given
        WorknetCodeMapper mapper = mapperWith(specWith(new WorknetApiSpecFile.Mapping(
                true, Map.of("SEOUL_JONGNO", "11110"), true, Map.of("IT01", "133"), Set.of())));

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("SEOUL_JONGNO"), List.of("IT01"))));

        // then
        assertThat(result.postings().get(0).regions()).containsExactly(SigunguCode.of("11110"));
        assertThat(result.postings().get(0).jobCodes()).containsExactly(JobCode.of("133"));
    }

    @Test
    @DisplayName("시도 단위 코드는 특정 시군구로 배분하지 않고 버린다")
    void dropsSidoLevelRegionCodeInsteadOfGuessing() {
        // given - 11000 은 '서울 전체'다. 어느 구인지 알 수 없다
        WorknetCodeMapper mapper = mapperWith(defaultSpec());

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("11000"), List.of("011"))));

        // then
        assertThat(result.postings()).isEmpty();
        assertThat(result.unresolvedRegionCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("시도 코드와 시군구 코드가 섞여 오면 시군구만 남긴다")
    void keepsOnlySigunguWhenMixedWithSidoCode() {
        // given
        WorknetCodeMapper mapper = mapperWith(defaultSpec());

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("11000", "11110"), List.of("011"))));

        // then
        assertThat(result.postings().get(0).regions()).containsExactly(SigunguCode.of("11110"));
    }

    @Test
    @DisplayName("ignoredRegionCodes 에 있는 코드는 조용히 버린다")
    void silentlyDropsExplicitlyIgnoredRegionCodes() {
        // given
        WorknetCodeMapper mapper = mapperWith(specWith(new WorknetApiSpecFile.Mapping(
                true, Map.of(), true, Map.of(), Set.of("99999"))));

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("99999", "11110"), List.of("011"))));

        // then
        assertThat(result.postings().get(0).regions()).containsExactly(SigunguCode.of("11110"));
        assertThat(result.unresolvedRegionCount()).isZero();
    }

    @Test
    @DisplayName("다지역·다직종 공고는 모든 조합에 1건씩 기여한다")
    void countsOncePerRegionJobCombination() {
        // given
        WorknetCodeMapper mapper = mapperWith(defaultSpec());

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("11110", "11140"), List.of("011", "012"))));

        // then
        assertThat(result.postings().get(0).countKeys()).hasSize(4);
    }

    @Test
    @DisplayName("구인인증번호가 없는 공고는 건너뛴다")
    void skipsPostingWithoutId() {
        // given
        WorknetCodeMapper mapper = mapperWith(defaultSpec());

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("  ", List.of("11110"), List.of("011"))));

        // then
        assertThat(result.postings()).isEmpty();
    }

    @Test
    @DisplayName("두 자리로 온 직종코드는 세 자리로 채워 비교한다")
    void padsJobCodeToThreeDigits() {
        // given
        WorknetCodeMapper mapper = mapperWith(defaultSpec());

        // when
        WorknetCodeMapper.PageMapping result = mapper.map(List.of(
                new WorknetJobPostingRaw("KJAU1", List.of("11110"), List.of("11"))));

        // then
        assertThat(result.postings().get(0).jobCodes()).containsExactly(JobCode.of("011"));
    }

    private WorknetApiSpecFile defaultSpec() {
        return WorknetApiSpecFile.defaults();
    }

    private WorknetApiSpecFile specWith(WorknetApiSpecFile.Mapping mapping) {
        return new WorknetApiSpecFile(null, null, mapping);
    }

    private WorknetCodeMapper mapperWith(WorknetApiSpecFile spec) {
        return new WorknetCodeMapper(new FixedSpecLoader(spec));
    }

    /** 파일을 읽지 않고 주어진 스펙만 돌려주는 로더. */
    private static final class FixedSpecLoader extends WorknetApiSpecLoader {

        private final WorknetApiSpecFile fixed;

        private FixedSpecLoader(WorknetApiSpecFile fixed) {
            super(new ObjectMapper(), new DefaultResourceLoader(), "classpath:worknet/absent.json");
            this.fixed = fixed;
        }

        @Override
        public WorknetApiSpecFile spec() {
            return fixed;
        }
    }
}
