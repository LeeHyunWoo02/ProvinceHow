package SDD.smash.global.batch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * seedMasterJob 의 Step 하나가 "돌아도 되는가"를 판정하는 데 필요한 정보.
 *
 * @param stepName        Step 빈의 이름이자 배치 메타의 STEP_NAME
 * @param group           필수 기준 데이터 / 외부 갱신 데이터
 * @param enabled         {@code seed.jobs.<키>.enabled}
 * @param guardParameter  재실행 판정 기준이 되는 JobParameter 이름
 *                        ({@code seedVersion} / {@code baseDate} / {@code baseMonth})
 * @param requiredConfigs 이 Step 이 요구하는 설정. <b>키만 로그에 남기고 값은 절대 남기지 않는다</b>
 *                        (API 키가 섞여 있다)
 * @param requiredTables  선행 기준 데이터 테이블. 비어 있으면 이 Step 을 돌리지 않는다
 * @param selfTable       이 Step 이 채우는 테이블. {@code null} 이 아니면
 *                        "이미 완료됨" 판정이 <b>테이블에 실제로 행이 있을 때만</b> 성립한다.
 *                        배치 메타는 남아 있는데 data DB 볼륨만 지운 상태에서
 *                        기준 데이터가 영영 다시 적재되지 않는 것을 막는다
 */
public record SeedStepSpec(
        String stepName,
        SeedGroup group,
        boolean enabled,
        String guardParameter,
        Map<String, String> requiredConfigs,
        List<String> requiredTables,
        String selfTable) {

    public static final String SEED_VERSION = "seedVersion";
    public static final String BASE_DATE = "baseDate";
    public static final String BASE_MONTH = "baseMonth";

    public SeedStepSpec {
        requiredConfigs = requiredConfigs == null ? Map.of() : new LinkedHashMap<>(requiredConfigs);
        requiredTables = requiredTables == null ? List.of() : List.copyOf(requiredTables);
    }

    /** 값이 비어 있는 설정 키 목록. 값 자체는 반환하지 않는다. */
    public List<String> missingConfigKeys() {
        List<String> missing = new ArrayList<>();
        requiredConfigs.forEach((key, value) -> {
            if (value == null || value.isBlank()) {
                missing.add(key);
            }
        });
        return missing;
    }
}
