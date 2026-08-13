package SDD.smash.domain.infra.infrastructure.master;

import SDD.smash.domain.infra.domain.model.IndustryCode;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 파싱된 업종 마스터 전체.
 *
 * @param entries           업종 목록(파일 순서 유지)
 * @param legacyServiceIds  구 LOCALDATA {@code opnSvcId} → 내부 업종 코드. 값이 {@code null} 인
 *                          항목은 <b>어느 업종인지 확인되지 않았다</b>는 뜻이며 레거시 CSV 를
 *                          읽을 때 임의 분류하지 않고 제외한다
 */
public record IndustryMaster(List<IndustryMasterEntry> entries, Map<String, String> legacyServiceIds) {

    public IndustryMaster {
        entries = entries == null ? List.of() : List.copyOf(entries);
        legacyServiceIds = legacyServiceIds == null
                ? Map.of()
                : java.util.Collections.unmodifiableMap(new LinkedHashMap<>(legacyServiceIds));
    }

    public static IndustryMaster empty() {
        return new IndustryMaster(List.of(), Map.of());
    }

    /** 수집·적재 대상 업종. {@code enabled=true} 이고 대분류가 확정된 것만이다. */
    public List<IndustryMasterEntry> active() {
        return entries.stream().filter(IndustryMasterEntry::isActive).toList();
    }

    /** 대분류가 미확정이거나 사람 검토를 받지 않은 항목. 기동 로그로 남긴다. */
    public List<IndustryMasterEntry> needingReview() {
        return entries.stream()
                .filter(entry -> entry.major() == null || !entry.majorReviewed())
                .toList();
    }

    public Optional<IndustryMasterEntry> byCode(IndustryCode code) {
        return entries.stream().filter(entry -> entry.code().equals(code)).findFirst();
    }

    /**
     * 구 {@code opnSvcId} 에 대응하는 내부 업종 코드.
     *
     * @return 매핑이 없거나 값이 비어 있으면 {@code Optional.empty()} — 호출부는 임의로 분류하지 말고 제외한다
     */
    public Optional<IndustryCode> byLegacyServiceId(String legacyServiceId) {
        if (legacyServiceId == null) {
            return Optional.empty();
        }
        String mapped = legacyServiceIds.get(legacyServiceId.trim());
        if (mapped == null || mapped.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(IndustryCode.of(mapped));
    }
}
