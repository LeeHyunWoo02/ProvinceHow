package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.Major;
import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 업종 마스터 YAML 파서. Spring 없이 도는 순수 로직이라 단위 테스트가 쉽다.
 *
 * <p>파일 형식은 {@code src/main/resources/infra/industry-master.yml} 을 참고한다.
 * YAML 을 고른 이유는 <b>주석</b>이다 — 대분류 배정은 서비스 기획 판단이라
 * {@code # 제안 — 사람 확인 필요} 같은 검토 표시를 파일 안에 남길 수 있어야 한다.
 *
 * <p>알 수 없는 {@code major} 문자열은 <b>예외를 던지지 않고</b> 미확정으로 처리한다.
 * 마스터 한 줄이 틀렸다고 배치 전체가 죽으면(기존 {@code Major.valueOf} 가 그랬다)
 * 운영에서 고치기 어렵다.
 */
@Slf4j
public final class IndustryMasterLoader {

    private static final String KEY_INDUSTRIES = "industries";
    private static final String KEY_LEGACY = "legacyServiceIds";

    /**
     * {@code industry.industry_code} 컬럼 길이({@code varchar(10)})를 그대로 반영한다.
     * 이보다 긴 코드는 적재 시점에 잘리거나 실패하므로 마스터를 읽을 때 미리 걸러낸다.
     * 스키마 지식이라 도메인이 아니라 여기에 둔다.
     */
    static final int MAX_CODE_LENGTH = 10;

    private IndustryMasterLoader() {
    }

    public static IndustryMaster load(InputStream in) {
        if (in == null) {
            return IndustryMaster.empty();
        }
        try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            Object parsed = new Yaml().load(reader);
            return toMaster(parsed);
        } catch (Exception e) {
            throw new IndustryMasterException("업종 마스터를 읽지 못했다: " + e.getClass().getSimpleName(), e);
        }
    }

    @SuppressWarnings("unchecked")
    private static IndustryMaster toMaster(Object parsed) {
        if (!(parsed instanceof Map<?, ?> root)) {
            return IndustryMaster.empty();
        }

        List<IndustryMasterEntry> entries = new ArrayList<>();
        Object industries = root.get(KEY_INDUSTRIES);
        if (industries instanceof List<?> list) {
            for (Object element : list) {
                if (element instanceof Map<?, ?> row) {
                    IndustryMasterEntry entry = toEntry((Map<String, Object>) row);
                    if (entry != null) {
                        entries.add(entry);
                    }
                }
            }
        }

        Map<String, String> legacy = new LinkedHashMap<>();
        Object legacyNode = root.get(KEY_LEGACY);
        if (legacyNode instanceof Map<?, ?> map) {
            map.forEach((key, value) -> {
                if (key != null) {
                    legacy.put(String.valueOf(key).trim(), value == null ? null : String.valueOf(value).trim());
                }
            });
        }

        return new IndustryMaster(entries, legacy);
    }

    private static IndustryMasterEntry toEntry(Map<String, Object> row) {
        String code = text(row.get("industryCode"));
        if (code == null) {
            log.warn("[industryMaster] industryCode 가 없는 항목을 건너뛴다.");
            return null;
        }
        if (code.length() > MAX_CODE_LENGTH) {
            log.warn("[industryMaster] industryCode 가 {}자를 넘어 건너뛴다. code={}", MAX_CODE_LENGTH, code);
            return null;
        }

        return new IndustryMasterEntry(
                IndustryCode.of(code),
                text(row.get("name")),
                toMajor(text(row.get("major")), code),
                text(row.get("slug")),
                text(row.get("datasetId")),
                bool(row.get("enabled"), true),
                bool(row.get("majorReviewed"), false),
                text(row.get("note")));
    }

    private static Major toMajor(String raw, String code) {
        if (raw == null || raw.equalsIgnoreCase("null")) {
            return null;
        }
        try {
            return Major.valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("[industryMaster] 알 수 없는 대분류라 미확정으로 둔다. code={}, major={}", code, raw);
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

    private static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean b) {
            return b;
        }
        if (value == null) {
            return fallback;
        }
        return Boolean.parseBoolean(String.valueOf(value).trim());
    }
}
