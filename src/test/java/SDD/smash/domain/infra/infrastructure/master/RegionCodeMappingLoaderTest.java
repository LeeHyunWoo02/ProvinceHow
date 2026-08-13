package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCodeMappingLoaderTest {

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("개방자치단체코드를 시군구코드로 바꾼다 - 산술 변환이 아니라 표를 따른다")
    void mapsOpenOrgCodeToSigunguCode() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "3000000"
                    sigunguCode: "11110"
                    name: 서울종로구
                  - openOrgCode: "3740000"
                    sigunguCode: "41110"
                    name: 경기수원시
                """));

        assertThat(mapping.size()).isEqualTo(2);
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3000000"))).contains(SigunguCode.of("11110"));
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3740000"))).contains(SigunguCode.of("41110"));
        assertThat(mapping.targets()).containsExactly(
                LocalDataRegionCode.of("3000000"), LocalDataRegionCode.of("3740000"));
    }

    @Test
    @DisplayName("매핑에 없는 코드는 비어 있는 결과를 준다 - 추정하지 않는다")
    void returnsEmptyForUnmappedCode() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "3000000"
                    sigunguCode: "11110"
                """));

        assertThat(mapping.toSigungu(LocalDataRegionCode.of("9999999"))).isEmpty();
        assertThat(mapping.toSigungu(null)).isEmpty();
    }

    @Test
    @DisplayName("형식이 잘못된 항목은 전체를 죽이지 않고 그 줄만 건너뛴다")
    void skipsMalformedEntries() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "300"
                    sigunguCode: "11110"
                    name: 자릿수오류
                  - openOrgCode: "3000000"
                    sigunguCode: "111"
                    name: 시군구자릿수오류
                  - openOrgCode: "3000000"
                    name: 시군구없음
                  - openOrgCode: "3010000"
                    sigunguCode: "11140"
                    name: 정상
                """));

        assertThat(mapping.size()).isEqualTo(1);
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3010000"))).contains(SigunguCode.of("11140"));
    }

    @Test
    @DisplayName("regions 가 없으면 빈 매핑이다")
    void returnsEmptyMappingWhenRegionsMissing() {
        assertThat(RegionCodeMappingLoader.load(yaml("other: 1")).isEmpty()).isTrue();
        assertThat(RegionCodeMappingLoader.load(yaml("")).isEmpty()).isTrue();
        assertThat(RegionCodeMappingLoader.load(null).isEmpty()).isTrue();
    }

    @Test
    @DisplayName("저장소의 실제 매핑 파일이 파싱된다")
    void loadsBundledMappingFile() {
        RegionCodeMapping mapping;
        try (InputStream in = getClass().getClassLoader()
                .getResourceAsStream("infra/localdata-region-mapping.yml")) {
            mapping = RegionCodeMappingLoader.load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThat(mapping.isEmpty()).isFalse();
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3000000"))).contains(SigunguCode.of("11110"));
    }
}
