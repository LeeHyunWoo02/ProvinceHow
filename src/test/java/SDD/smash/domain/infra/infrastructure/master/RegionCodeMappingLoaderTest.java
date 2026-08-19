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
    @DisplayName("개방자치단체코드가 중복돼도 항목을 버리지 않고 그대로 싣는다 - 제외는 대상 계획이 한다")
    void keepsDuplicateEntriesAndLeavesExclusionToThePlan() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "3000000"
                    sigunguCode: "11110"
                    name: 서울종로구
                  - openOrgCode: "3000000"
                    sigunguCode: "11110"
                    name: 서울종로구(중복)
                """));

        // 로더는 경고만 남긴다. asMap 은 putIfAbsent 라 먼저 온 것이 이긴다
        assertThat(mapping.size()).isEqualTo(2);
        assertThat(mapping.asMap()).hasSize(1);
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3000000"))).contains(SigunguCode.of("11110"));
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
    @DisplayName("districtSplits 를 읽어 상위 시별 하위 구 분해 규칙을 만든다")
    void loadsDistrictSplits() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "3740000"
                    sigunguCode: "41110"
                    name: 경기수원시
                districtSplits:
                  - parentSigunguCode: "41110"
                    cityName: 수원시
                    districts:
                      - name: 장안구
                        sigunguCode: "41111"
                      - name: 권선구
                        sigunguCode: "41113"
                """));

        assertThat(mapping.districtSplits()).hasSize(1);
        RegionCodeMapping.DistrictSplit split = mapping.districtSplits().get(0);
        assertThat(split.parentSigunguCode()).isEqualTo(SigunguCode.of("41110"));
        assertThat(split.cityName()).isEqualTo("수원시");
        assertThat(split.resolve("경기도 수원시 장안구 정자동 1")).contains(SigunguCode.of("41111"));
        assertThat(split.resolve("경기도 수원시 권선구 세류동 2")).contains(SigunguCode.of("41113"));
    }

    @Test
    @DisplayName("districtSplits 가 없는 옛 형식 파일도 그대로 로드된다 - 하위 호환")
    void loadsLegacyYamlWithoutDistrictSplits() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "3000000"
                    sigunguCode: "11110"
                    name: 서울종로구
                """));

        assertThat(mapping.size()).isEqualTo(1);
        assertThat(mapping.districtSplits()).isEmpty();
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3000000"))).contains(SigunguCode.of("11110"));
    }

    @Test
    @DisplayName("형식이 잘못된 분해 항목은 전체를 죽이지 않고 그 블록만 건너뛴다")
    void skipsMalformedDistrictSplits() {
        RegionCodeMapping mapping = RegionCodeMappingLoader.load(yaml("""
                regions:
                  - openOrgCode: "3740000"
                    sigunguCode: "41110"
                    name: 경기수원시
                districtSplits:
                  - cityName: 상위코드없음
                    districts:
                      - name: 장안구
                        sigunguCode: "41111"
                  - parentSigunguCode: "411"
                    cityName: 상위코드자릿수오류
                    districts:
                      - name: 장안구
                        sigunguCode: "41111"
                  - parentSigunguCode: "41130"
                    cityName: 하위구없음
                    districts: []
                  - parentSigunguCode: "41170"
                    cityName: 하위구전부오류
                    districts:
                      - name: 만안구
                        sigunguCode: "411"
                  - parentSigunguCode: "41110"
                    cityName: 수원시
                    districts:
                      - name: 장안구
                        sigunguCode: "41111"
                      - name: 코드없음
                """));

        assertThat(mapping.districtSplits()).hasSize(1);
        RegionCodeMapping.DistrictSplit split = mapping.districtSplits().get(0);
        assertThat(split.cityName()).isEqualTo("수원시");
        assertThat(split.districts()).hasSize(1);
    }

    @Test
    @DisplayName("저장소의 실제 매핑 파일이 파싱된다 - 시군구 228 + 세종 1 = 229건")
    void loadsBundledMappingFile() {
        RegionCodeMapping mapping = bundled();

        assertThat(mapping.size()).isEqualTo(229);
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3000000"))).contains(SigunguCode.of("11110"));
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3740000"))).contains(SigunguCode.of("41110"));
    }

    @Test
    @DisplayName("세종특별자치시는 시도 본청 코드 5690000 이 곧 시군구 36110 이다")
    void mapsSejongViaProvincialHeadquarterCode() {
        assertThat(bundled().toSigungu(LocalDataRegionCode.of("5690000")))
                .contains(SigunguCode.of("36110"));
    }

    @Test
    @DisplayName("실제 매핑 파일의 일반구 분해는 12개 시 35개 구다")
    void loadsBundledDistrictSplits() {
        RegionCodeMapping mapping = bundled();

        assertThat(mapping.districtSplits()).hasSize(12);
        assertThat(mapping.districtSplits().stream()
                .mapToInt(split -> split.districts().size())
                .sum()).isEqualTo(35);

        assertThat(mapping.splitOf(SigunguCode.of("41110")))
                .hasValueSatisfying(split ->
                        assertThat(split.resolve("경기도 수원시 장안구 정자동 1"))
                                .contains(SigunguCode.of("41111")));
        assertThat(mapping.splitOf(SigunguCode.of("48120")))
                .hasValueSatisfying(split -> {
                    assertThat(split.resolve("경상남도 창원시 마산합포구 오동동 1"))
                            .contains(SigunguCode.of("48125"));
                    assertThat(split.resolve("경상남도 창원시 마산회원구 석전동 2"))
                            .contains(SigunguCode.of("48127"));
                });
        // 일반구가 없는 자치단체는 분해 대상이 아니다.
        assertThat(mapping.splitOf(SigunguCode.of("11110"))).isEmpty();
        assertThat(mapping.splitOf(SigunguCode.of("36110"))).isEmpty();
    }

    private static RegionCodeMapping bundled() {
        try (InputStream in = RegionCodeMappingLoaderTest.class.getClassLoader()
                .getResourceAsStream("infra/localdata-region-mapping.yml")) {
            return RegionCodeMappingLoader.load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
