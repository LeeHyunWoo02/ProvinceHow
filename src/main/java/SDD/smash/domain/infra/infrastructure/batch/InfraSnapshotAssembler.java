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
 * 그래서 수집을 끝내지 못하면 예외를 던지고, 그 결과 Step 이 FAILED 로 끝나 기존 스냅샷이 보존된다.
 *
 * <h2>실행 내 2차 패스</h2>
 * 1차 순회에서 대상(지역 × 업종)이 실패해도 즉시 죽지 않고 <b>실패 목록에 모아 두고 계속</b> 진행한다.
 * 순회가 끝나면 실패한 대상만 <b>한 번 더</b> 수집한다. 2차 패스에서도 남은 실패가 있으면 그때
 * {@code InfraCollectionException} 을 던져 전체를 실패시킨다 — 부분 반영은 여전히 금지다.
 * 3,664개 대상을 19분 수집한 뒤 한 대상의 읽기 타임아웃으로 전량을 버린 2026-08 장애가 계기다.
 *
 * <p>누계({@code apiCalls}/{@code readCount}/{@code filteredOut}/{@code duplicates}/재분배 건수)는
 * 수집에 <b>성공한 대상에서만</b> 더해진다. 1차 실패분은 아무것도 반영되지 않으므로 2차 패스가
 * 성공해도 중복 집계되지 않는다. {@code targets} 는 대상 수라서 1차에서 한 번만 센다.
 *
 * <h2>수집 경로</h2>
 * {@code infra.collect.source} = {@code API}(기본) / {@code BULK_CSV} / {@code LEGACY_CSV}.
 *
 * <h2>제외 규칙</h2>
 * <ul>
 *   <li>업종 마스터에 없는 서비스 식별자 → <b>임의 분류하지 않고</b> 로그를 남기고 제외</li>
 *   <li>시군구 매핑에 없는 개방자치단체코드 → 로그를 남기고 제외(주소 문자열로 추정하지 않는다)</li>
 *   <li>영업/정상(01)이 아닌 사업장 → 개수에서 제외</li>
 *   <li>일반구 시인데 주소에서 구를 못 찾은 사업장 → 로그를 남기고 제외(§일반구 재분배)</li>
 * </ul>
 *
 * <h2>일반구 재분배</h2>
 * LOCALDATA 는 인허가 권한이 있는 시 단위로만 자료를 줘서 수원·성남 등 12개 시는 개방자치단체코드
 * 하나가 일반구 시군구코드 여러 개에 대응한다. 이 경우에만 <b>사업장 주소 문자열</b>로 하위 구를
 * 가른다(정책 결정 2026-08-13). 주소 후보 순서와 구 이름 매칭 규칙은
 * {@code InfraFacility.addressCandidates()} 와 {@code RegionCodeMapping.DistrictSplit} 이 정의한다.
 *
 * <p><b>구를 못 찾으면 상위 시 코드로 떨어뜨리지 않고 버린다.</b> 상위 시(예: 수원시 41110)도
 * {@code sigungu} 테이블에 실재하므로 그대로 적재하면 41111~41117 과 <b>같은 사업장이 두 번</b>
 * 집계된다. 시군구 내 구성비({@code ratio})와 업종별 전국 백분위({@code score}) 둘 다
 * 왜곡되므로, 일부 손실을 감수하고 제외한 뒤 건수를 로그로 드러낸다.
 *
 * <p>재분배는 {@code ratio}/{@code score} 계산 <b>전</b>에 끝난다 — 개수 집계 맵에 하위 구 코드로
 * 적립한 뒤 그 맵 전체를 {@code InfraStatPolicy} 에 넘기므로, 두 값은 일반구 단위로 계산된다.
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

        // ★ 순서가 중요하다. counts 에는 이미 일반구 재분배가 끝난 코드만 들어 있고,
        //   ratio/score 는 그 맵 전체를 보고 계산되므로 백분위 모집단이 일반구 단위다.
        List<RegionIndustryStat> stats = new InfraStatPolicy(ratioBasis).stats(collected.counts);

        return new InfraSnapshot(stats, collected.targets, collected.apiCalls, collected.readCount,
                collected.filteredOut, collected.duplicates,
                collected.unmappedRegions.size(), collected.unmappedIndustries.size(),
                collected.districtResolved, collected.districtUnresolved);
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
        Map<SigunguCode, RegionCodeMapping.DistrictSplit> splits = mapping.splitIndex();
        Collected collected = new Collected();

        List<Target> targets = new ArrayList<>(mapping.entries().size() * industries.size());
        for (RegionCodeMapping.Entry region : mapping.entries()) {
            for (IndustryMasterEntry industry : industries) {
                targets.add(new Target(industry.code(), region.openOrgCode()));
            }
        }
        // targets 는 "대상 수"다. 2차 패스는 같은 대상을 다시 부르는 것이므로 여기서만 센다.
        collected.targets = targets.size();
        log.info("[infraJob] 수집 시작 source={}, regions={}, industries={}, targets={}",
                source, mapping.entries().size(), industries.size(), targets.size());

        // 1차 순회. 대상 하나가 실패해도 즉시 죽지 않고 모아 둔다 — 3,664개 대상을 19분 수집한 뒤
        // 한 건 때문에 전량 폐기한 것이 2026-08 장애의 실제 손실이었다.
        List<Failure> failures = runPass(provider, targets, collected, index, splits);
        log.info("[infraJob] 1차 수집 완료 성공={}건 / 실패={}건", targets.size() - failures.size(), failures.size());

        if (!failures.isEmpty()) {
            // 누계(apiCalls/readCount/...)는 성공한 대상에서만 더해진다. 1차 실패분은 아무것도
            // 반영되지 않았으므로 2차 패스가 성공해도 중복 집계가 생기지 않는다.
            log.info("[infraJob] 1차 실패 {}건 → 2차 패스 시작. 첫 실패={}",
                    failures.size(), failures.get(0).reason());

            List<Failure> remaining = runPass(provider, toTargets(failures), collected, index, splits);
            log.info("[infraJob] 2차 패스 결과 성공 {}건 / 실패 {}건",
                    failures.size() - remaining.size(), remaining.size());

            if (!remaining.isEmpty()) {
                // 여전히 결측이 있으면 백분위 모집단이 깨진다. 부분 반영은 금지다.
                Failure first = remaining.get(0);
                throw new InfraCollectionException(String.format(
                        "[infraJob] 2차 패스에서도 수집 실패로 스냅샷을 만들 수 없다. 남은 실패=%d건, 첫 실패=%s",
                        remaining.size(), first.reason()), first.cause());
            }
        }

        if (!collected.unmappedRegions.isEmpty()) {
            log.warn("[infraJob] 시군구 매핑에 없는 개방자치단체코드 {}종({}건)을 제외했다. codes={}",
                    collected.unmappedRegions.size(), collected.unmappedFacilityCount,
                    collected.unmappedRegions);
        }
        if (collected.districtUnresolved > 0) {
            log.warn("[infraJob] 주소에서 일반구를 찾지 못해 {}건을 제외했다(상위 시로 떨어뜨리지 않는다)."
                            + " resolved={}, cities={}",
                    collected.districtUnresolved, collected.districtResolved, collected.unresolvedCities);
        } else if (collected.districtResolved > 0) {
            log.info("[infraJob] 일반구 재분배 완료 resolved={}, unresolved=0", collected.districtResolved);
        }

        collected.counts = toCounts(collected.merged);
        return collected;
    }

    /**
     * 대상 목록을 한 번 훑는다. 실패한 대상은 <b>던지지 않고</b> 돌려준다.
     *
     * <p>누계와 개수 맵은 {@code collect} 가 성공한 대상에서만 갱신된다. 실패한 대상은
     * {@code accumulate} 를 아예 통과하지 않으므로, 그 대상이 2차 패스에서 성공해도 값이
     * 두 번 더해지지 않는다.
     */
    private List<Failure> runPass(InfraFacilityProvider provider, List<Target> targets, Collected collected,
                                  Map<LocalDataRegionCode, SigunguCode> index,
                                  Map<SigunguCode, RegionCodeMapping.DistrictSplit> splits) {

        List<Failure> failures = new ArrayList<>();
        for (Target target : targets) {
            FacilityCollection collection;
            try {
                collection = collect(provider, target.industryCode(), target.regionCode());
            } catch (InfraCollectionException e) {
                // 건수가 많을 수 있어 대상 단위 상세는 debug 다. 집계는 호출부가 info 로 남긴다.
                log.debug("[infraJob] 대상 수집 실패 industry={}, org={}, reason={}",
                        target.industryCode().value(), target.regionCode().value(), e.getMessage());
                failures.add(new Failure(target, e));
                continue;
            }
            accumulate(collection, target, collected, index, splits);
        }
        return failures;
    }

    /** 성공한 수집 결과 하나를 누계와 개수 맵에 반영한다. 여기 들어온 대상은 다시 수집되지 않는다. */
    private void accumulate(FacilityCollection collection, Target target, Collected collected,
                            Map<LocalDataRegionCode, SigunguCode> index,
                            Map<SigunguCode, RegionCodeMapping.DistrictSplit> splits) {

        collected.apiCalls += collection.apiCalls();
        collected.readCount += collection.readCount();
        collected.filteredOut += collection.filteredOutCount();
        collected.duplicates += collection.duplicatesDropped();

        // 시군구 접기 규칙(매핑·일반구 재분배)은 체크포인트 경로와 한 곳을 공유한다.
        InfraFacilityTally.Result tally = InfraFacilityTally.tally(collection, index, splits);

        collected.unmappedRegions.addAll(tally.unmappedRegions());
        collected.unmappedFacilityCount += tally.unmappedFacilityCount();
        collected.districtResolved += tally.districtResolved();
        collected.districtUnresolved += tally.districtUnresolved();
        collected.unresolvedCities.addAll(tally.unresolvedCities());

        tally.counts().forEach((sigunguCode, count) ->
                collected.merged.merge(new Key(sigunguCode, target.industryCode()), count, Integer::sum));
    }

    private static List<Target> toTargets(List<Failure> failures) {
        List<Target> targets = new ArrayList<>(failures.size());
        for (Failure failure : failures) {
            targets.add(failure.target());
        }
        return targets;
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

    /**
     * 설정이 고른 공급자. 체크포인트 수집 경로({@code infraCollectStep})도 <b>같은 선택</b>을 써야
     * 두 경로가 갈라지지 않으므로 같은 패키지에 열어 둔다.
     */
    InfraFacilityProvider provider() {
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
            collected.merged.merge(new Key(sigunguCode, industryCode), num, Integer::sum);
        }

        if (!collected.unmappedIndustries.isEmpty()) {
            log.warn("[infraJob] 업종 마스터에 매핑되지 않은 레거시 opnSvcId {}건을 제외했다. ids={}",
                    collected.unmappedIndustries.size(), collected.unmappedIndustries);
        }
        if (!collected.unmappedRegions.isEmpty()) {
            log.warn("[infraJob] 시군구 코드 형식이 잘못된 {}건을 제외했다. codes={}",
                    collected.unmappedRegions.size(), collected.unmappedRegions);
        }

        collected.counts = toCounts(collected.merged);
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

    /** 수집 대상 하나. 지역(개방자치단체코드) × 업종이다. */
    private record Target(IndustryCode industryCode, LocalDataRegionCode regionCode) {
    }

    /** 1차 순회에서 실패한 대상과 그 사유. 2차 패스의 입력이다. */
    private record Failure(Target target, InfraCollectionException cause) {
        private String reason() {
            return cause.getMessage();
        }
    }

    /** 수집 중간 상태. 지표를 한 곳에 모은다. */
    private static final class Collected {
        /** 지역×업종 개수 누계. 성공한 대상만 여기에 더해진다. */
        private final Map<Key, Integer> merged = new LinkedHashMap<>();
        private List<RegionIndustryCount> counts = List.of();
        private int targets;
        private int apiCalls;
        private int readCount;
        private int filteredOut;
        private int duplicates;
        private int unmappedFacilityCount;
        private int districtResolved;
        private int districtUnresolved;
        private final Set<String> unmappedRegions = new LinkedHashSet<>();
        private final Set<String> unmappedIndustries = new LinkedHashSet<>();
        private final Set<String> unresolvedCities = new LinkedHashSet<>();
    }
}
