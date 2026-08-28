package SDD.smash.domain.infra.infrastructure.external;

import SDD.smash.domain.infra.domain.model.BusinessStatus;
import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.InfraFacility;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.infrastructure.master.IndustryMaster;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class LocalDataBulkCsvAdapterTest {

    private static final LocalDataRegionCode JONGNO = LocalDataRegionCode.of("3000000");

    private final LocalDataBulkCsvAdapter adapter = new LocalDataBulkCsvAdapter(
            RestClient.create(), mock(InfraMasterCatalog.class),
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
    @DisplayName("지번주소와 도로명주소를 둘 다 읽는다 - 일반구 재분배의 단서다")
    void readsBothAddressColumns() {
        String csv = "관리번호,영업상태코드,개방자치단체코드,도로명주소,지번주소\n"
                + "S-1,01,3740000,경기도 수원시 장안구 정자로 2,경기도 수원시 장안구 정자동 1\n";

        List<InfraFacility> facilities = adapter.parse(csv, JONGNO);

        assertThat(facilities).hasSize(1);
        assertThat(facilities.get(0).lotAddress()).isEqualTo("경기도 수원시 장안구 정자동 1");
        assertThat(facilities.get(0).roadAddress()).isEqualTo("경기도 수원시 장안구 정자로 2");
        assertThat(facilities.get(0).addressCandidates())
                .containsExactly("경기도 수원시 장안구 정자동 1", "경기도 수원시 장안구 정자로 2");
    }

    @Test
    @DisplayName("주소 컬럼이 없어도 실패하지 않는다 - 일반구가 없는 자치단체는 코드로 확정된다")
    void toleratesMissingAddressColumns() {
        List<InfraFacility> facilities = adapter.parse("관리번호,영업상태코드\nA-1,01\n", JONGNO);

        assertThat(facilities).hasSize(1);
        assertThat(facilities.get(0).addressCandidates()).isEmpty();
    }

    // ------------------------------------------------------------------ HTTP 왕복

    @Test
    @DisplayName("Referer 를 실어 보내고 CP949 본문을 디코딩해 사업장으로 읽는다")
    void sendsRefererAndDecodesCp949Body() {
        // given - Referer 가 빠지면 302 로 튕겨 전량 0건이 된다. parse() 테스트로는 잡히지 않는 구간이다
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        // 한글 주소를 CP949 로 인코딩해 둔다. UTF-8 로 읽으면 이 단언이 깨진다
        byte[] cp949 = """
                관리번호,영업상태코드,지번주소
                A-1,01,서울특별시 종로구 청진동 1
                """.getBytes(Charset.forName("MS949"));

        server.expect(requestTo("https://file.localdata.go.kr/file/download/GenralRestrnt/info?orgCode=3000000"))
                .andExpect(header(HttpHeaders.REFERER, "https://www.data.go.kr/"))
                .andExpect(header(HttpHeaders.ACCEPT, "application/octet-stream, */*"))
                .andRespond(withSuccess(cp949, MediaType.APPLICATION_OCTET_STREAM));

        LocalDataBulkCsvAdapter httpAdapter = new LocalDataBulkCsvAdapter(
                builder.build(), catalogWithSlug("GenralRestrnt"),
                "https://file.localdata.go.kr/file/download", "https://www.data.go.kr/", 0);

        // when
        FacilityCollection collection = httpAdapter.collect(IndustryCode.of("I56011"), JONGNO);

        // then
        assertThat(collection.readCount()).isEqualTo(1);
        assertThat(collection.facilities().get(0).managementNo()).isEqualTo("A-1");
        assertThat(collection.facilities().get(0).lotAddress()).isEqualTo("서울특별시 종로구 청진동 1");
        server.verify();
    }

    /** 업종 코드 하나에 slug 를 붙여 둔 마스터. URL 조립에만 쓰인다. */
    private static InfraMasterCatalog catalogWithSlug(String slug) {
        InfraMasterCatalog catalog = mock(InfraMasterCatalog.class);
        IndustryMasterEntry entry = new IndustryMasterEntry(
                IndustryCode.of("I56011"), "일반음식점", Major.FOOD, slug, "ds-1", true, true, null);
        given(catalog.industryMaster()).willReturn(new IndustryMaster(List.of(entry), Map.of()));
        return catalog;
    }

    @Test
    @DisplayName("인증키 없이 쓸 수 있지만 Referer 는 필수다")
    void requiresRefererButNoServiceKey() {
        assertThat(adapter.isReady()).isTrue();

        LocalDataBulkCsvAdapter withoutReferer = new LocalDataBulkCsvAdapter(
                RestClient.create(), mock(InfraMasterCatalog.class),
                "https://file.localdata.go.kr/file/download", "  ", 0);

        assertThat(withoutReferer.isReady()).isFalse();
        assertThat(withoutReferer.readinessDescription()).contains("Referer");
    }
}
