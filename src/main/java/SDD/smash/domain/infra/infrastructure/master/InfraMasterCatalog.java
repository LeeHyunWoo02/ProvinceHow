package SDD.smash.domain.infra.infrastructure.master;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * 업종 마스터와 지역코드 매핑을 한 번 읽어 들고 있는 컴포넌트.
 *
 * <p>두 파일 모두 <b>운영에서 교체할 수 있어야</b> 하므로 위치를 프로퍼티로 받는다.
 * 기본값은 classpath 리소스이고, 운영에서는 {@code file:} 경로를 주면 재빌드 없이 바꿀 수 있다.
 *
 * <pre>
 * infra.industry-master.location=classpath:infra/industry-master.yml
 * infra.region-mapping.location=classpath:infra/localdata-region-mapping.yml
 * </pre>
 *
 * <p>기동 시 <b>검토가 필요한 업종 수와 매핑 규모를 로그로 남긴다.</b> 대분류 배정은
 * 서비스 기획 판단이라 코드가 추론하지 않으며, 미확정 항목은 수집 대상에서 빠진다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InfraMasterCatalog {

    private final ResourceLoader resourceLoader;

    @Value("${infra.industry-master.location:classpath:infra/industry-master.yml}")
    private String industryMasterLocation;

    @Value("${infra.region-mapping.location:classpath:infra/localdata-region-mapping.yml}")
    private String regionMappingLocation;

    /**
     * 기존 {@code industry.filePath} 프로퍼티의 호환 경로.
     *
     * <p>{@code SeedMasterJobConfig} 의 관문이 {@code industryStep} 의 필수 설정으로
     * {@code industry.filePath} 를 보고 있어(값이 비면 Step 을 건너뛴다), 이 값이 채워져 있으면
     * <b>업종 마스터 파일 위치로 해석</b>한다. {@code classpath:} / {@code file:} 접두어를 쓸 수 있다.
     * 비어 있으면 {@code infra.industry-master.location} 을 쓴다.
     */
    @Value("${industry.filePath:}")
    private String legacyIndustryLocation;

    private volatile IndustryMaster industryMaster = IndustryMaster.empty();
    private volatile RegionCodeMapping regionCodeMapping = RegionCodeMapping.empty();

    @PostConstruct
    void loadAll() {
        this.industryMaster = readIndustryMaster();
        this.regionCodeMapping = readRegionMapping();

        int active = industryMaster.active().size();
        int review = industryMaster.needingReview().size();
        log.info("[infraMaster] 업종 마스터 로드 total={}, active={}, needsReview={}, regionMappings={}",
                industryMaster.entries().size(), active, review, regionCodeMapping.size());

        if (review > 0) {
            log.warn("[infraMaster] 대분류(Major) 확정이 필요한 업종 {}건 — codes={}", review,
                    industryMaster.needingReview().stream()
                            .map(entry -> entry.code().value())
                            .toList());
        }
        if (regionCodeMapping.isEmpty()) {
            log.warn("[infraMaster] 지역코드 매핑이 비어 있다. 수집 대상이 없어 인프라 배치가 아무것도 적재하지 않는다."
                    + " location={}", regionMappingLocation);
        }
    }

    public IndustryMaster industryMaster() {
        return industryMaster;
    }

    public RegionCodeMapping regionCodeMapping() {
        return regionCodeMapping;
    }

    private IndustryMaster readIndustryMaster() {
        String location = (legacyIndustryLocation != null && !legacyIndustryLocation.isBlank())
                ? legacyIndustryLocation
                : industryMasterLocation;
        try (InputStream in = open(location)) {
            if (in == null) {
                log.warn("[infraMaster] 업종 마스터 파일이 없다. location={}", location);
                return IndustryMaster.empty();
            }
            return IndustryMasterLoader.load(in);
        } catch (IOException e) {
            throw new IndustryMasterException("업종 마스터 파일을 열지 못했다: " + location, e);
        }
    }

    private RegionCodeMapping readRegionMapping() {
        try (InputStream in = open(regionMappingLocation)) {
            if (in == null) {
                log.warn("[infraMaster] 지역코드 매핑 파일이 없다. location={}", regionMappingLocation);
                return RegionCodeMapping.empty();
            }
            return RegionCodeMappingLoader.load(in);
        } catch (IOException e) {
            throw new IndustryMasterException("지역코드 매핑 파일을 열지 못했다: " + regionMappingLocation, e);
        }
    }

    private InputStream open(String location) throws IOException {
        if (location == null || location.isBlank()) {
            return null;
        }
        Resource resource = resourceLoader.getResource(location.trim());
        return resource.exists() ? resource.getInputStream() : null;
    }
}
