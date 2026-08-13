package SDD.smash.domain.infra.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BusinessStatusTest {

    @Test
    @DisplayName("공식 영업상태코드 6종이 모두 매핑된다")
    void mapsAllOfficialStatusCodes() {
        assertThat(BusinessStatus.fromCode("01")).isEqualTo(BusinessStatus.OPERATING);
        assertThat(BusinessStatus.fromCode("02")).isEqualTo(BusinessStatus.SUSPENDED);
        assertThat(BusinessStatus.fromCode("03")).isEqualTo(BusinessStatus.CLOSED);
        assertThat(BusinessStatus.fromCode("04")).isEqualTo(BusinessStatus.REVOKED);
        assertThat(BusinessStatus.fromCode("05")).isEqualTo(BusinessStatus.REMOVED);
        assertThat(BusinessStatus.fromCode("06")).isEqualTo(BusinessStatus.ETC);
    }

    @Test
    @DisplayName("영업/정상(01)만 인프라 개수에 포함된다")
    void countsOnlyOperatingStatus() {
        assertThat(BusinessStatus.OPERATING.countsAsInfra()).isTrue();
        assertThat(BusinessStatus.SUSPENDED.countsAsInfra()).isFalse();
        assertThat(BusinessStatus.CLOSED.countsAsInfra()).isFalse();
        assertThat(BusinessStatus.REVOKED.countsAsInfra()).isFalse();
        assertThat(BusinessStatus.REMOVED.countsAsInfra()).isFalse();
        assertThat(BusinessStatus.ETC.countsAsInfra()).isFalse();
    }

    @Test
    @DisplayName("공백과 한 자리 표기를 허용한다")
    void acceptsTrimmedAndSingleDigitCodes() {
        assertThat(BusinessStatus.fromCode(" 01 ")).isEqualTo(BusinessStatus.OPERATING);
        assertThat(BusinessStatus.fromCode("1")).isEqualTo(BusinessStatus.OPERATING);
    }

    @Test
    @DisplayName("코드표에 없는 값은 null 이라 호출부가 제외를 판단한다")
    void returnsNullForUnknownCode() {
        assertThat(BusinessStatus.fromCode("99")).isNull();
        assertThat(BusinessStatus.fromCode("")).isNull();
        assertThat(BusinessStatus.fromCode(null)).isNull();
    }
}
