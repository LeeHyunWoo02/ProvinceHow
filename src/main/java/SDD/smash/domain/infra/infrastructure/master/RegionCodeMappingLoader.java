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
import java.util.List;
import java.util.Map;

/** 개방자치단체코드 ↔ 시군구코드 매핑 YAML 파서. Spring 없이 도는 순수 로직이다. */
@Slf4j
public final class RegionCodeMappingLoader {

    private static final String KEY_REGIONS = "regions";

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
        for (Object element : list) {
            if (element instanceof Map<?, ?> row) {
                RegionCodeMapping.Entry entry = toEntry(row);
                if (entry != null) {
                    entries.add(entry);
                }
            }
        }
        return new RegionCodeMapping(entries);
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
