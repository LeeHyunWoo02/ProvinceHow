package SDD.smash.domain.infra.domain.model;

import SDD.smash.global.exception.DomainException;
import SDD.smash.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InfraFacilityTest {

    private static final LocalDataRegionCode SUWON = LocalDataRegionCode.of("3740000");

    @Test
    @DisplayName("관리번호가 없으면 중복 제거 키가 없어 만들 수 없다")
    void rejectsBlankManagementNo() {
        assertThatThrownBy(() -> new InfraFacility("  ", BusinessStatus.OPERATING, SUWON))
                .isInstanceOf(DomainException.class)
                .extracting(e -> ((DomainException) e).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_FAILED);
    }

    @Test
    @DisplayName("주소 후보는 지번주소가 먼저다 - 도로명주소 결측률이 42.8% 다")
    void prefersLotAddressOverRoadAddress() {
        InfraFacility facility = new InfraFacility("A-1", BusinessStatus.OPERATING, SUWON,
                "경기도 수원시 장안구 정자동 1", "경기도 수원시 장안구 정자로 2");

        assertThat(facility.addressCandidates())
                .containsExactly("경기도 수원시 장안구 정자동 1", "경기도 수원시 장안구 정자로 2");
    }

    @Test
    @DisplayName("빈 주소는 후보에서 빠진다 - 공백만 있는 값도 없는 것으로 본다")
    void dropsBlankAddresses() {
        InfraFacility onlyRoad = new InfraFacility("A-1", BusinessStatus.OPERATING, SUWON,
                "   ", "경기도 수원시 장안구 정자로 2");
        InfraFacility none = new InfraFacility("A-2", BusinessStatus.OPERATING, SUWON, null, null);

        assertThat(onlyRoad.lotAddress()).isNull();
        assertThat(onlyRoad.addressCandidates()).containsExactly("경기도 수원시 장안구 정자로 2");
        assertThat(none.addressCandidates()).isEmpty();
    }

    @Test
    @DisplayName("주소 없이도 사업장을 만들 수 있다 - 일반구가 없는 지역은 코드로 확정된다")
    void allowsFacilityWithoutAddress() {
        InfraFacility facility = new InfraFacility("A-1", BusinessStatus.OPERATING, SUWON);

        assertThat(facility.lotAddress()).isNull();
        assertThat(facility.roadAddress()).isNull();
        assertThat(facility.countsAsInfra()).isTrue();
    }
}
