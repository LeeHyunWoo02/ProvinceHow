package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.domain.model.FacilityCollection;
import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.domain.infra.domain.model.RatioBasis;
import SDD.smash.domain.infra.domain.model.RegionIndustryCount;
import SDD.smash.domain.infra.domain.model.RegionIndustryStat;
import SDD.smash.domain.infra.domain.port.InfraFacilityProvider;
import SDD.smash.domain.infra.domain.service.InfraStatPolicy;
import SDD.smash.domain.infra.infrastructure.batch.dto.InfraSnapshot;
import SDD.smash.domain.infra.infrastructure.external.LocalDataApiAdapter;
import SDD.smash.domain.infra.infrastructure.external.LocalDataBulkCsvAdapter;
import SDD.smash.domain.infra.infrastructure.master.IndustryMaster;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.InfraMasterCatalog;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 인프라 스냅샷을 <b>통째로</b> 만든다. {@code infraStep} 의 Reader 가 이 결과를 흘려보낸다.
 *
 * <h2>왜 통째로인가</h2>
 * {@code ratio} 는 시군구 내 전 업종 합계를, {@code score} 는 업종별 전국 분포를 알아야 계산된다.
 * 행 단위 스트리밍으로는 계산할 수 없다. 그리고 <b>부분 수집 스냅샷은 반영하면 안 된다</b> —
 * 일부 업종만 갱신되면 두 값이 서로 다른 기준으로 섞이기 때문이다.
 * 그래서 수집 도중 실패하면 예외를 던지고, 그 결과 Step 이 FAILED 로 끝나 기존 스냅샷이 보존된다.
 *
 * <h2>수집 경로</h2>
 * {@code infra.collect.source} = {@code API}(기본) / {@code BULK_CSV} / {@code LEGACY_CSV}.
 *
 * <h2>제외 규칙</h2>
 * <ul>
 *   <li>업종 마스터에 없는 서비스 식별자 → <b>임의 분류하지 않고</b> 로그를 남기고 제외</li>
 *   <li>시군구 매핑에 없는 개방자치단체코드 → 로그를 남기고 제외(주소 문자열로 추정하지 않는다)</li>
 *   <li>영업/정상(01)이 아닌 사업장 → 개수에서 제외</li>
 * </ul>
 */
@Component
@Slf4j
public class InfraSnapshotAssembler {

    private final InfraMasterCatalog masterCatalog;
    private final LocalDataApiAdapter apiAdapter;
    private final LocalDataBulkCsvAdapter bulkCsvAdapter;

    private final InfraCollectSource source;
    private final RatioBasis ratioBasis;
    private final String legacyCsvPath;
    private final String legacyCsvEncoding;

    public InfraSnapshotAssembler(
            InfraMasterCatalog masterCatalog,
            LocalDataApiAdapter apiAdapter,
            LocalDataBulkCsvAdapter bulkCsvAdapter,
            @Value("${infra.collect.source:API}") String source,
            @Value("${infra.ratio.basis:PERCENT}") String ratioBasis,
            @Value("${infra.legacy-csv.path:data/legacy/infra.csv}") String legacyCsvPath,
            @Value("${infra.legacy-csv.encoding:UTF-8}") String legacyCsvEncoding,
            @Value("${infra.filePath:}") String legacyFilePathOverride) {

        this.masterCatalog = masterCatalog;
        this.apiAdapter = apiAdapter;
        this.bulkCsvAdapter = bulkCsvAdapter;
        this.source = parseSource(source);
        this.ratioBasis = parseRatioBasis(ratioBasis);
        // 기존 infra.filePath 프로퍼티 호환. SeedMasterJobConfig 의 관문이 이 키를 보고 있다.
        this.legacyCsvPath = (legacyFilePathOverride == null || legacyFilePathOverride.isBlank())
                ? legacyCsvPath
                : legacyFilePathOverride;
        this.legacyCsvEncoding = legacyCsvEncoding;
    }

    public InfraCollectSource source() {
        return source;
    }

    public RatioBasis ratioBasis() {
        return ratioBasis;
    }

    /** 이 수집 경로가 지금 쓸 수 있는 상태인가. 아니면 배치를 건너뛴다. */
    public boolean isReady() {
        return source == InfraCollectSource.LEGACY_CSV || provider().isReady();
    }

    public String readinessDescription() {
        return source == InfraCollectSource.LEGACY_CSV
                ? "레거시 CSV 경로(외부 호출 없음): " + legacyCsvPath
                : provider().readinessDescription();
    }

    /**
     * 스냅샷 전체를 만든다.
     *
     * @throws InfraCollectionException 수집을 완료하지 못했을 때(부분 반영 금지)
     */
    public InfraSnapshot assemble() {
        IndustryMaster master = masterCatalog.industryMaster();
        List<IndustryMasterEntry> industries = master.active();
        if (industries.isEmpty()) {
            log.warn("[infraJob] 활성 업종이 없다. 업종 마스터의 major/enabled 를 확인하라.");
            return InfraSnapshot.empty();
        }

        Collected collected = (source == InfraCollectSource.LEGACY_CSV)
                ? fromLegacyCsv(master)
                : fromProvider(industries);

        List<RegionIndustryStat> stats = new InfraStatPolicy(ratioBasis).stats(collected.counts);

        return new InfraSnapshot(stats, collected.targets, collected.apiCalls, collected.readCount,
                collected.filteredOut, collected.duplicates,
                collected.unmappedRegions.size(), collected.unmappedIndustries.size());
    }

    // ------------------------------------------------------------------ 외부 수집

    private Collected fromProvider(List<IndustryMasterEntry> industries) {
        InfraFacilityProvider provider = provider();
        RegionCodeMapping mapping = masterCatalog.regionCodeMapping();
        if (mapping.isEmpty()) {
            throw new InfraCollectionException(
                    "[infraJob] 지역코드 매핑이 비어 있어 수집 대상이 없다. infra/localdata-region-mapping.yml 을 채워라.");
        }

        Map<LocalDataRegionCode, SigunguCode> index = mapping.asMap();
        Collected collected = new Collected();
        Map<Key, Integer> counts = new LinkedHashMap<>();

        for (RegionCodeMapping.Entry region : mapping.entries()) {
            for (IndustryMasterEntry industry : industries) {
                collected.targets++;
                FacilityCollection collection = collect(provider, industry.code(), region.openOrgCode());

                collected.apiCalls += collection.apiCalls();
                collected.readCount += collection.readCount();
                collected.filteredOut += collection.filteredOutCount();
                collected.duplicates += collection.duplicatesDropped();

                // 사업장이 들고 있는 개방자치단체코드를 우선한다. 요청이 시도 전체(_ALL)일 수 있어
                // 요청 코드로 뭉뚱그리면 시군구가 뭉개진다. 주소 문자열은 쓰지 않는다.
                for (var facility : collection.facilities()) {
                    if (!facility.countsAsInfra()) {
                        continue;
                    }
                    LocalDataRegionCode orgCode = facility.openOrgCode();
                    SigunguCode sigunguCode = orgCode == null ? null : index.get(orgCode);
                    if (sigunguCode == null) {
                        collected.unmappedRegions.add(orgCode == null ? "(없음)" : orgCode.value());
                        collected.unmappedFacilityCount++;
                        continue;
                    }
                    counts.merge(new Key(sigunguCode, industry.code()), 1, Integer::sum);
                }
            }
        }

        if (!collected.unmappedRegions.isEmpty()) {
            log.warn("[infraJob] 시군구 매핑에 없는 개방자치단체코드 {}종({}건)을 제외했다. codes={}",
                    collected.unmappedRegions.size(), collected.unmappedFacilityCount,
                    collected.unmappedRegions);
        }

        collected.counts = toCounts(counts);
        return collected;
    }

    private FacilityCollection collect(InfraFacilityProvider provider,
                                       IndustryCode industryCode, LocalDataRegionCode regionCode) {
        try {
            return provider.collect(industryCode, regionCode);
        } catch (RuntimeException e) {
            throw new InfraCollectionException(String.format(
                    "[infraJob] 수집 실패로 스냅샷을 만들 수 없다. industry=%s, org=%s, reason=%s",
                    industryCode.value(), regionCode.value(), e.getMessage()), e);
        }
    }

    private InfraFacilityProvider provider() {
        return source == InfraCollectSource.BULK_CSV ? bulkCsvAdapter : apiAdapter;
    }

    // ------------------------------------------------------------------ 레거시 CSV

    /**
     * {@code sigungu_code,opnSvcId,num} 형식을 읽는다.
     *
     * <p>구 {@code opnSvcId} 는 업종 마스터의 {@code legacyServiceIds} 에 <b>명시적으로 등록된 것만</b>
     * 내부 업종 코드로 바뀐다. 등록되지 않은 값은 어느 업종인지 확인되지 않았다는 뜻이므로
     * 추정하지 않고 제외한다.
     */
    private Collected fromLegacyCsv(IndustryMaster master) {
        Collected collected = new Collected();
        Path path = Path.of(legacyCsvPath);
        if (!Files.exists(path)) {
            throw new InfraCollectionException("[infraJob] 레거시 CSV 가 없다: " + legacyCsvPath);
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(path, Charset.forName(legacyCsvEncoding));
        } catch (IOException | RuntimeException e) {
            throw new InfraCollectionException("[infraJob] 레거시 CSV 를 읽지 못했다: " + legacyCsvPath, e);
        }

        Map<Key, Integer> counts = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = stripBom(lines.get(i));
            if (line == null || line.isBlank()) {
                continue;
            }
            String[] columns = line.split(",", -1);
            if (columns.length < 3) {
                continue;
            }
            collected.targets++;

            String rawSigungu = columns[0].trim();
            String legacyServiceId = columns[1].trim();
            int num = parseInt(columns[2].trim());

            IndustryCode industryCode = master.byLegacyServiceId(legacyServiceId).orElse(null);
            if (industryCode == null) {
                collected.unmappedIndustries.add(legacyServiceId);
                continue;
            }
            SigunguCode sigunguCode;
            try {
                sigunguCode = SigunguCode.of(rawSigungu);
            } catch (RuntimeException e) {
                collected.unmappedRegions.add(rawSigungu);
                continue;
            }
            collected.readCount += num;
            counts.merge(new Key(sigunguCode, industryCode), num, Integer::sum);
        }

        if (!collected.unmappedIndustries.isEmpty()) {
            log.warn("[infraJob] 업종 마스터에 매핑되지 않은 레거시 opnSvcId {}건을 제외했다. ids={}",
                    collected.unmappedIndustries.size(), collected.unmappedIndustries);
        }
        if (!collected.unmappedRegions.isEmpty()) {
            log.warn("[infraJob] 시군구 코드 형식이 잘못된 {}건을 제외했다. codes={}",
                    collected.unmappedRegions.size(), collected.unmappedRegions);
        }

        collected.counts = toCounts(counts);
        return collected;
    }

    // ------------------------------------------------------------------ 보조

    private static List<RegionIndustryCount> toCounts(Map<Key, Integer> merged) {
        List<RegionIndustryCount> counts = new ArrayList<>(merged.size());
        merged.forEach((key, count) ->
                counts.add(new RegionIndustryCount(key.sigunguCode(), key.industryCode(), count)));
        return counts;
    }

    private static int parseInt(String raw) {
        try {
            return Math.max(0, Integer.parseInt(raw));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static String stripBom(String line) {
        return (line != null && !line.isEmpty() && line.charAt(0) == '﻿') ? line.substring(1) : line;
    }

    private static InfraCollectSource parseSource(String raw) {
        if (raw == null || raw.isBlank()) {
            return InfraCollectSource.API;
        }
        try {
            return InfraCollectSource.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("[infraJob] 알 수 없는 수집 경로라 기본값 API 로 돌린다. infra.collect.source={}", raw);
            return InfraCollectSource.API;
        }
    }

    private static RatioBasis parseRatioBasis(String raw) {
        if (raw == null || raw.isBlank()) {
            return RatioBasis.DEFAULT;
        }
        try {
            return RatioBasis.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("[infraJob] 알 수 없는 ratio 기준이라 기본값 {} 로 돌린다. infra.ratio.basis={}",
                    RatioBasis.DEFAULT, raw);
            return RatioBasis.DEFAULT;
        }
    }

    private record Key(SigunguCode sigunguCode, IndustryCode industryCode) {
    }

    /** 수집 중간 상태. 지표를 한 곳에 모은다. */
    private static final class Collected {
        private List<RegionIndustryCount> counts = List.of();
        private int targets;
        private int apiCalls;
        private int readCount;
        private int filteredOut;
        private int duplicates;
        private int unmappedFacilityCount;
        private final Set<String> unmappedRegions = new LinkedHashSet<>();
        private final Set<String> unmappedIndustries = new LinkedHashSet<>();
    }
}
