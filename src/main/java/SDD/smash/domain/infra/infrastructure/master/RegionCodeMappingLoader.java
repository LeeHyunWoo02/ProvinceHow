package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.LocalDataRegionCode;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.global.exception.DomainException;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 개방자치단체코드 ↔ 시군구코드 매핑 YAML 파서. Spring 없이 도는 순수 로직이다. */
@Slf4j
public final class RegionCodeMappingLoader {

    private static final String KEY_REGIONS = "regions";
    private static final String KEY_DISTRICT_SPLITS = "districtSplits";

    private RegionCodeMappingLoader() {
    }

    public static RegionCodeMapping load(InputStream in) {
        if (in == null) {
            return RegionCodeMapping.empty();
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object parsed = new Yaml().load(reader);
            return toMapping(parsed);
        } catch (Exception e) {
            throw new IndustryMasterException(
                    "지역코드 매핑을 읽지 못했다: " + e.getClass().getSimpleName(), e);
        }
    }

    private static RegionCodeMapping toMapping(Object parsed) {
        if (!(parsed instanceof Map<?, ?> root)) {
            return RegionCodeMapping.empty();
        }
        Object regions = root.get(KEY_REGIONS);
        if (!(regions instanceof List<?> list)) {
            return RegionCodeMapping.empty();
        }

        List<RegionCodeMapping.Entry> entries = new ArrayList<>(list.size());
        Set<LocalDataRegionCode> seen = new LinkedHashSet<>(list.size());
        List<String> duplicated = new ArrayList<>();
        for (Object element : list) {
            if (element instanceof Map<?, ?> row) {
                RegionCodeMapping.Entry entry = toEntry(row);
                if (entry != null) {
                    if (!seen.add(entry.openOrgCode())) {
                        duplicated.add(entry.openOrgCode().value());
                    }
                    entries.add(entry);
                }
            }
        }
        if (!duplicated.isEmpty()) {
            // 중복은 수집 대상을 두 배로 만들고 staging 카운트를 이중 합산시킨다.
            // 파일을 여기서 고칠 수는 없으므로 드러내기만 한다 — 실제 제외는 대상 목록을
            // 만드는 InfraCollectPlan.allTargets 가 한다.
            log.warn("[infra] 지역 매핑에 개방자치단체코드가 중복된다 codes={} - 파일을 확인하라.", duplicated);
        }
        return new RegionCodeMapping(entries, toDistrictSplits(root.get(KEY_DISTRICT_SPLITS)));
    }

    /**
     * {@code districtSplits} 는 <b>선택</b> 항목이다. 없으면 빈 목록이라 옛 형식 파일도 그대로 읽힌다.
     */
    private static List<RegionCodeMapping.DistrictSplit> toDistrictSplits(Object parsed) {
        if (!(parsed instanceof List<?> list)) {
            return List.of();
        }
        List<RegionCodeMapping.DistrictSplit> splits = new ArrayList<>(list.size());
        for (Object element : list) {
            if (element instanceof Map<?, ?> row) {
                RegionCodeMapping.DistrictSplit split = toDistrictSplit(row);
                if (split != null) {
                    splits.add(split);
                }
            }
        }
        return splits;
    }

    private static RegionCodeMapping.DistrictSplit toDistrictSplit(Map<?, ?> row) {
        String parentSigunguCode = text(row.get("parentSigunguCode"));
        String cityName = text(row.get("cityName"));

        if (parentSigunguCode == null) {
            log.warn("[regionMapping] parentSigunguCode 가 없는 일반구 분해 항목을 건너뛴다. cityName={}", cityName);
            return null;
        }
        if (!(row.get("districts") instanceof List<?> rawDistricts) || rawDistricts.isEmpty()) {
            log.warn("[regionMapping] districts 가 비어 있어 일반구 분해 항목을 건너뛴다. parent={}", parentSigunguCode);
            return null;
        }

        List<RegionCodeMapping.District> districts = new ArrayList<>(rawDistricts.size());
        for (Object element : rawDistricts) {
            if (element instanceof Map<?, ?> districtRow) {
                RegionCodeMapping.District district = toDistrict(districtRow, parentSigunguCode);
                if (district != null) {
                    districts.add(district);
                }
            }
        }
        if (districts.isEmpty()) {
            log.warn("[regionMapping] 유효한 하위 구가 하나도 없어 분해 항목을 건너뛴다. parent={}", parentSigunguCode);
            return null;
        }

        try {
            return new RegionCodeMapping.DistrictSplit(
                    SigunguCode.of(parentSigunguCode), cityName, districts);
        } catch (DomainException e) {
            log.warn("[regionMapping] 형식이 잘못된 상위 시 코드라 분해 항목을 건너뛴다. parent={}, reason={}",
                    parentSigunguCode, e.getMessage());
            return null;
        }
    }

    private static RegionCodeMapping.District toDistrict(Map<?, ?> row, String parentSigunguCode) {
        String name = text(row.get("name"));
        String sigunguCode = text(row.get("sigunguCode"));

        if (name == null || sigunguCode == null) {
            log.warn("[regionMapping] name/sigunguCode 가 없는 하위 구를 건너뛴다. parent={}, name={}",
                    parentSigunguCode, name);
            return null;
        }
        try {
            return new RegionCodeMapping.District(name, SigunguCode.of(sigunguCode));
        } catch (DomainException e) {
            log.warn("[regionMapping] 형식이 잘못된 하위 구를 건너뛴다. parent={}, name={}, sigunguCode={}, reason={}",
                    parentSigunguCode, name, sigunguCode, e.getMessage());
            return null;
        }
    }

    private static RegionCodeMapping.Entry toEntry(Map<?, ?> row) {
        String openOrgCode = text(row.get("openOrgCode"));
        String sigunguCode = text(row.get("sigunguCode"));
        String name = text(row.get("name"));

        if (openOrgCode == null || sigunguCode == null) {
            log.warn("[regionMapping] openOrgCode/sigunguCode 가 없는 항목을 건너뛴다. name={}", name);
            return null;
        }
        try {
            return new RegionCodeMapping.Entry(
                    LocalDataRegionCode.of(openOrgCode), SigunguCode.of(sigunguCode), name);
        } catch (DomainException e) {
            log.warn("[regionMapping] 형식이 잘못된 항목을 건너뛴다. openOrgCode={}, sigunguCode={}, reason={}",
                    openOrgCode, sigunguCode, e.getMessage());
            return null;
        }
    }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || text.equalsIgnoreCase("null") ? null : text;
    }
}
