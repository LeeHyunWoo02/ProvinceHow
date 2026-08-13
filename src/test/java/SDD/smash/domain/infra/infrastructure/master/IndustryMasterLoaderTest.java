package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.Major;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class IndustryMasterLoaderTest {

    private static InputStream yaml(String text) {
        return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("업종 항목과 레거시 opnSvcId 매핑을 함께 읽는다")
    void loadsIndustriesAndLegacyServiceIds() {
        IndustryMaster master = IndustryMasterLoader.load(yaml("""
                industries:
                  - industryCode: RESTAURANT
                    name: 일반음식점
                    major: FOOD
                    majorReviewed: true
                    slug: general_restaurants
                    datasetId: "15154916"
                    enabled: true
                legacyServiceIds:
                  "07_24_04_P": RESTAURANT
                  "11_44_01_P": null
                """));

        assertThat(master.entries()).hasSize(1);
        IndustryMasterEntry entry = master.entries().get(0);
        assertThat(entry.code()).isEqualTo(IndustryCode.of("RESTAURANT"));
        assertThat(entry.major()).isEqualTo(Major.FOOD);
        assertThat(entry.slug()).isEqualTo("general_restaurants");
        assertThat(entry.isActive()).isTrue();

        assertThat(master.byLegacyServiceId("07_24_04_P")).contains(IndustryCode.of("RESTAURANT"));
        assertThat(master.byLegacyServiceId("11_44_01_P")).isEmpty();
        assertThat(master.byLegacyServiceId("99_99_99_P")).isEmpty();
    }

    @Test
    @DisplayName("major 가 null 이면 미확정이라 수집·적재 대상에서 빠진다")
    void excludesEntryWithoutMajor() {
        IndustryMaster master = IndustryMasterLoader.load(yaml("""
                industries:
                  - industryCode: TOBACCO
                    name: 담배소매업
                    major: null
                    slug: tobacco_retailers
                    enabled: false
                """));

        assertThat(master.entries()).hasSize(1);
        assertThat(master.active()).isEmpty();
        assertThat(master.needingReview()).hasSize(1);
    }

    @Test
    @DisplayName("알 수 없는 major 문자열은 배치를 죽이지 않고 미확정으로 처리된다")
    void treatsUnknownMajorAsUndecided() {
        IndustryMaster master = IndustryMasterLoader.load(yaml("""
                industries:
                  - industryCode: WEIRD
                    name: 오타업종
                    major: FOODD
                    slug: whatever
                    enabled: true
                """));

        assertThat(master.entries().get(0).major()).isNull();
        assertThat(master.active()).isEmpty();
    }

    @Test
    @DisplayName("majorReviewed 가 false 여도 대분류가 있으면 적재된다 - 검토 목록에만 남는다")
    void loadsProposedMajorButFlagsForReview() {
        IndustryMaster master = IndustryMasterLoader.load(yaml("""
                industries:
                  - industryCode: FITNESS
                    name: 체력단련장업
                    major: CULTURE
                    majorReviewed: false
                    slug: fitness_centers
                    enabled: true
                """));

        assertThat(master.active()).hasSize(1);
        assertThat(master.needingReview()).hasSize(1);
    }

    @Test
    @DisplayName("industry_code 컬럼 길이(10자)를 넘는 코드는 건너뛴다")
    void skipsTooLongIndustryCode() {
        IndustryMaster master = IndustryMasterLoader.load(yaml("""
                industries:
                  - industryCode: TOO_LONG_INDUSTRY_CODE
                    name: 너무긴코드
                    major: LIFE
                    slug: x
                """));

        assertThat(master.entries()).isEmpty();
    }

    @Test
    @DisplayName("빈 입력이면 빈 마스터다")
    void returnsEmptyMasterForBlankInput() {
        assertThat(IndustryMasterLoader.load(yaml("")).entries()).isEmpty();
        assertThat(IndustryMasterLoader.load(null).entries()).isEmpty();
    }

    @Test
    @DisplayName("저장소의 실제 마스터 파일이 파싱되고 모든 활성 업종에 slug 가 있다")
    void loadsBundledMasterFile() {
        IndustryMaster master;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("infra/industry-master.yml")) {
            master = IndustryMasterLoader.load(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        assertThat(master.entries()).isNotEmpty();
        assertThat(master.active()).isNotEmpty();
        assertThat(master.active()).allSatisfy(entry -> {
            assertThat(entry.slug()).isNotBlank();
            assertThat(entry.major()).isNotNull();
            assertThat(entry.code().value().length()).isLessThanOrEqualTo(10);
        });
        // 레거시 CSV 의 opnSvcId 14종이 전부 등록돼 있어야 "미확인"도 명시적으로 남는다.
        assertThat(master.legacyServiceIds()).hasSize(14);
    }
}
