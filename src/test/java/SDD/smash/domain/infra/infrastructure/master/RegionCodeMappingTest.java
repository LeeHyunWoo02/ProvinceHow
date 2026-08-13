package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping.District;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping.DistrictSplit;
import SDD.smash.global.domain.model.SigunguCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RegionCodeMappingTest {

    private static final SigunguCode SUWON = SigunguCode.of("41110");
    private static final SigunguCode CHANGWON = SigunguCode.of("48120");
    private static final SigunguCode GOYANG = SigunguCode.of("41280");

    private static DistrictSplit suwon() {
        return new DistrictSplit(SUWON, "수원시", List.of(
                new District("장안구", SigunguCode.of("41111")),
                new District("권선구", SigunguCode.of("41113")),
                new District("팔달구", SigunguCode.of("41115")),
                new District("영통구", SigunguCode.of("41117"))));
    }

    private static DistrictSplit changwon() {
        return new DistrictSplit(CHANGWON, "창원시", List.of(
                new District("마산합포구", SigunguCode.of("48125")),
                new District("마산회원구", SigunguCode.of("48127")),
                new District("의창구", SigunguCode.of("48121")),
                new District("성산구", SigunguCode.of("48123")),
                new District("진해구", SigunguCode.of("48129"))));
    }

    private static DistrictSplit goyang() {
        return new DistrictSplit(GOYANG, "고양시", List.of(
                new District("일산동구", SigunguCode.of("41285")),
                new District("일산서구", SigunguCode.of("41287")),
                new District("덕양구", SigunguCode.of("41281"))));
    }

    @Test
    @DisplayName("주소의 구 이름으로 하위 일반구 코드를 찾는다")
    void resolvesDistrictFromAddress() {
        assertThat(suwon().resolve("경기도 수원시 장안구 정자동 111-1"))
                .contains(SigunguCode.of("41111"));
        assertThat(suwon().resolve("경기도 수원시 영통구 매탄동 200"))
                .contains(SigunguCode.of("41117"));
    }

    @Test
    @DisplayName("창원시 마산합포구와 마산회원구를 서로 섞지 않는다")
    void distinguishesMasanDistricts() {
        assertThat(changwon().resolve("경상남도 창원시 마산합포구 오동동 1"))
                .contains(SigunguCode.of("48125"));
        assertThat(changwon().resolve("경상남도 창원시 마산회원구 석전동 2"))
                .contains(SigunguCode.of("48127"));
    }

    @Test
    @DisplayName("고양시 일산동구와 일산서구를 서로 섞지 않는다")
    void distinguishesIlsanDistricts() {
        assertThat(goyang().resolve("경기도 고양시 일산동구 장항동 1"))
                .contains(SigunguCode.of("41285"));
        assertThat(goyang().resolve("경기도 고양시 일산서구 대화동 2"))
                .contains(SigunguCode.of("41287"));
        assertThat(goyang().resolve("경기도 고양시 덕양구 행신동 3"))
                .contains(SigunguCode.of("41281"));
    }

    @Test
    @DisplayName("YAML 순서와 무관하게 긴 이름을 먼저 맞춘다 - 파일을 재정렬해도 깨지지 않는다")
    void matchesLongerNameFirstRegardlessOfDeclarationOrder() {
        // 짧은 이름(다른 이름의 접두어)이 먼저 선언된 최악의 순서를 일부러 만든다.
        DistrictSplit split = new DistrictSplit(SigunguCode.of("41110"), "가상시", List.of(
                new District("산구", SigunguCode.of("41111")),
                new District("산구남구", SigunguCode.of("41113"))));

        assertThat(split.districts().get(0).name()).isEqualTo("산구남구");
        assertThat(split.resolve("가상시 산구남구 어딘가 1")).contains(SigunguCode.of("41113"));
        assertThat(split.resolve("가상시 산구 어딘가 1")).contains(SigunguCode.of("41111"));
    }

    @Test
    @DisplayName("구 이름이 여러 번 나오면 가장 앞에 나온 것이 행정구역이다")
    void picksLeftmostDistrictWhenAddressMentionsSeveral() {
        DistrictSplit seongnam = new DistrictSplit(SigunguCode.of("41130"), "성남시", List.of(
                new District("수정구", SigunguCode.of("41131")),
                new District("중원구", SigunguCode.of("41133")),
                new District("분당구", SigunguCode.of("41135"))));

        assertThat(seongnam.resolve("경기도 성남시 분당구 정자동 178-1 수정구빌딩"))
                .contains(SigunguCode.of("41135"));
    }

    @Test
    @DisplayName("구 이름이 없는 주소는 상위 시로 떨어지지 않고 빈 결과다")
    void returnsEmptyWhenAddressHasNoDistrict() {
        assertThat(suwon().resolve("경기도 수원시 매산로 1")).isEmpty();
        assertThat(suwon().resolve("  ")).isEmpty();
        assertThat(suwon().resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("주소 후보를 순서대로 훑어 처음 걸리는 구를 쓴다 - 지번주소가 먼저다")
    void resolvesFromFirstUsableAddressCandidate() {
        assertThat(suwon().resolveAny(List.of("경기도 수원시 매산로 1", "경기도 수원시 팔달구 매산로1가 18")))
                .contains(SigunguCode.of("41115"));
        assertThat(suwon().resolveAny(List.of("경기도 수원시 매산로 1"))).isEmpty();
        assertThat(suwon().resolveAny(List.of())).isEmpty();
        assertThat(suwon().resolveAny(null)).isEmpty();
    }

    @Test
    @DisplayName("분해 대상이 아닌 시군구는 분해 규칙이 없다")
    void hasNoSplitForOrdinarySigungu() {
        RegionCodeMapping mapping = new RegionCodeMapping(
                List.of(new RegionCodeMapping.Entry(
                        LocalDataRegionCode.of("3740000"), SUWON, "경기수원시")),
                List.of(suwon()));

        assertThat(mapping.splitOf(SUWON)).isPresent();
        assertThat(mapping.splitOf(SigunguCode.of("11110"))).isEmpty();
        assertThat(mapping.splitOf(null)).isEmpty();
        assertThat(mapping.splitIndex()).containsOnlyKeys(SUWON);
    }

    @Test
    @DisplayName("districtSplits 없이 만든 매핑도 그대로 동작한다 - 하위 호환")
    void keepsWorkingWithoutDistrictSplits() {
        RegionCodeMapping mapping = new RegionCodeMapping(List.of(
                new RegionCodeMapping.Entry(LocalDataRegionCode.of("3000000"),
                        SigunguCode.of("11110"), "서울종로구")));

        assertThat(mapping.districtSplits()).isEmpty();
        assertThat(mapping.splitIndex()).isEmpty();
        assertThat(mapping.toSigungu(LocalDataRegionCode.of("3000000")))
                .contains(SigunguCode.of("11110"));
    }
}
