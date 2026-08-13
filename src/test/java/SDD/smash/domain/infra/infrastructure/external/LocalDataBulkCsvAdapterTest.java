package SDD.smash.domain.infra.infrastructure.external;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class LocalDataBulkCsvAdapterTest {

    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");

    private final LocalDataBulkCsvAdapter adapter = new LocalDataBulkCsvAdapter(
            new RestTemplate(), mock(InfraMasterCatalog.class),
            "https://file.localdata.go.kr/file/download", "https://www.data.go.kr/", 0);

    /** 실측 헤더(일반음식점 39컬럼)의 앞부분 순서를 그대로 쓴다. */
    private static final String HEADER =
            "개방자치단체코드,관리번호,인허가일자,영업상태명,폐업일자,영업상태코드,사업장명";

    @Test
    @DisplayName("헤더 이름으로 컬럼을 찾아 사업장을 읽는다")
    void parsesFacilitiesByHeaderName() {
        String csv = HEADER + "\n"
                + "3000000,3000000-101-1999-10679,1999-09-03,폐업,2004-01-06,03,혜원감자탕\n"
                + "3000000,3000000-101-2016-00350,2016-01-01,영업,,01,좋은식당\n";

        List<InfraFacility> facilities = adapter.parse(csv, JONGNO);

        assertThat(facilities).hasSize(2);
        assertThat(facilities.get(0).status()).isEqualTo(BusinessStatus.CLOSED);
        assertThat(facilities.get(1).status()).isEqualTo(BusinessStatus.OPERATING);
        assertThat(facilities.get(1).managementNo()).isEqualTo("3000000-101-2016-00350");
        assertThat(facilities.get(1).openOrgCode()).isEqualTo(JONGNO);
    }

    @Test
    @DisplayName("컬럼 순서가 달라도 헤더 이름으로 찾으므로 동작한다")
    void toleratesDifferentColumnOrder() {
        String csv = "관리번호,사업장명,영업상태코드,개방자치단체코드\n"
                + "A-1,가게,01,3010000\n";

        List<InfraFacility> facilities = adapter.parse(csv, JONGNO);

        assertThat(facilities).hasSize(1);
        assertThat(facilities.get(0).status()).isEqualTo(BusinessStatus.OPERATING);
        assertThat(facilities.get(0).openOrgCode()).isEqualTo(LocalDataRegionCode.of("3010000"));
    }

    @Test
    @DisplayName("큰따옴표 안의 콤마는 컬럼 구분자로 보지 않는다")
    void keepsCommasInsideQuotedValues() {
        String csv = "관리번호,사업장명,영업상태코드\n"
                + "\"A-1\",\"김밥, 라면\",01\n";

        List<InfraFacility> facilities = adapter.parse(csv, JONGNO);

        assertThat(facilities).hasSize(1);
        assertThat(facilities.get(0).managementNo()).isEqualTo("A-1");
    }

    @Test
    @DisplayName("필수 컬럼이 없으면 0건으로 삼키지 않고 실패한다 - 형식 변경을 조용히 넘기면 스냅샷이 망가진다")
    void failsWhenRequiredColumnsMissing() {
        assertThatThrownBy(() -> adapter.parse("사업장명,전화번호\n가게,02-000-0000\n", JONGNO))
                .isInstanceOf(LocalDataApiException.class)
                .hasMessageContaining("필수 컬럼");
    }

    @Test
    @DisplayName("관리번호가 비어 있는 행은 중복 제거 키가 없어 건너뛴다")
    void skipsRowsWithoutManagementNo() {
        String csv = "관리번호,영업상태코드\n"
                + ",01\n"
                + "A-2,01\n";

        assertThat(adapter.parse(csv, JONGNO)).hasSize(1);
    }

    @Test
    @DisplayName("인증키 없이 쓸 수 있지만 Referer 는 필수다")
    void requiresRefererButNoServiceKey() {
        assertThat(adapter.isReady()).isTrue();

        LocalDataBulkCsvAdapter withoutReferer = new LocalDataBulkCsvAdapter(
                new RestTemplate(), mock(InfraMasterCatalog.class),
                "https://file.localdata.go.kr/file/download", "  ", 0);

        assertThat(withoutReferer.isReady()).isFalse();
        assertThat(withoutReferer.readinessDescription()).contains("Referer");
    }
}
