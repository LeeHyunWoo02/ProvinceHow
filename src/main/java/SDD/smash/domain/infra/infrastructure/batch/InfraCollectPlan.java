package SDD.smash.domain.infra.infrastructure.batch;

import SDD.smash.domain.infra.infrastructure.batch.dto.InfraCollectTarget;
import SDD.smash.domain.infra.infrastructure.master.IndustryMasterEntry;
import SDD.smash.domain.infra.infrastructure.master.RegionCodeMapping;
import SDD.smash.domain.infra.infrastructure.persistence.InfraCollectionStagingStore.TargetKey;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 수집 회차와 남은 대상을 계산한다. <b>순수 함수</b>라 DB 없이 검증된다.
 *
 * <h2>회차 키</h2>
 * {@code baseDate} 를 회차로 쓰면 날짜가 바뀌는 순간 이어받기가 끊긴다. 그래서 회차를 따로 둔다.
 * <ul>
 *   <li>staging 에 남아 있는 회차가 있으면 <b>가장 오래된 것</b>을 이어받는다.</li>
 *   <li>없으면 오늘 날짜로 새 회차를 연다.</li>
 * </ul>
 * 반영이 끝나면 그 회차의 staging 을 지우므로, 다음 실행은 자연히 새 회차가 된다.
 * 완성됐지만 반영에 실패한 회차가 남아 있는 경우에도 "가장 오래된 것"이 그 회차를 먼저 집어
 * 재반영을 시도하게 된다.
 *
 * <h2>완성 판정</h2>
 * 행 수 비교가 아니라 <b>대상 집합의 포함 관계</b>로 본다. 지역 매핑이나 활성 업종이 중간에
 * 바뀌어 기대 대상 수가 늘거나 줄어도 판정이 흔들리지 않는다.
 */
@Slf4j
final class InfraCollectPlan {

    private InfraCollectPlan() {
    }

    /**
     * 이어받을 회차를 고른다. <b>정책은 "가장 오래된 회차 하나"</b>다.
     *
     * <p>정상 상태에서 회차는 최대 하나다 — 반영에 성공하면 그 회차를 지우기 때문이다. 둘 이상이
     * 보인다면 정리 실패 잔여물이거나 수동 개입의 흔적이고, 선택되지 않은 회차는 이어받지도
     * 정리되지도 않은 채 staging 에 남는다. 어느 쪽을 지울지는 <b>사람이 판단할 문제</b>라
     * 여기서 임의로 지우지 않고 목록을 드러내기만 한다.
     *
     * @param existingRunKeys staging 에 남아 있는 회차 키. <b>오래된 것부터</b> 정렬돼 있어야 한다
     */
    static String runKey(List<String> existingRunKeys, LocalDate today) {
        if (existingRunKeys == null || existingRunKeys.isEmpty()) {
            return today.toString();
        }
        String oldest = existingRunKeys.get(0);
        if (existingRunKeys.size() > 1) {
            log.warn("[infraJob] staging 에 회차가 {}개 남아 있다 {} - 가장 오래된 {} 만 이어받는다. "
                            + "나머지는 이어받지도 정리되지도 않으므로 확인이 필요하다.",
                    existingRunKeys.size(), existingRunKeys, oldest);
        }
        return oldest;
    }

    /**
     * 기대 대상 전체 = 지역 매핑 × 활성 업종. 이 목록이 회차 완성의 기준이다.
     *
     * <h3>왜 중복을 제거하는가</h3>
     * 매핑 파일에 같은 {@code openOrgCode} 가 두 줄 있으면 같은 (기관, 업종) 대상이 pending 에
     * 두 번 들어간다. 진행 행은 유니크 upsert 라 한 번이지만 카운트는
     * {@code count + VALUES(count)} 합산이라 <b>두 번 더해진다.</b> "이미 진행 행이 있는 대상은
     * 다시 수집하지 않는다"는 방어는 <b>실행 사이</b>만 막고 <b>같은 실행 안</b>은 막지 못한다.
     * 그래서 대상 목록을 만드는 이 지점에서 {@code (기관, 업종)} 기준으로 한 번만 남긴다.
     */
    static List<InfraCollectTarget> allTargets(RegionCodeMapping mapping, List<IndustryMasterEntry> industries) {
        int expected = mapping.entries().size() * Math.max(1, industries.size());
        // record 라 equals 가 (업종, 기관) 값 비교다. 파일 순서는 유지된다.
        Set<InfraCollectTarget> distinct = new LinkedHashSet<>(Math.max(16, expected));
        int duplicates = 0;
        for (RegionCodeMapping.Entry region : mapping.entries()) {
            for (IndustryMasterEntry industry : industries) {
                if (!distinct.add(new InfraCollectTarget(industry.code(), region.openOrgCode()))) {
                    duplicates++;
                }
            }
        }
        if (duplicates > 0) {
            log.warn("[infraJob] 지역 매핑에 중복 개방자치단체코드가 있어 대상 {}건을 제외했다. "
                    + "매핑 파일을 확인하라. targets={}", duplicates, distinct.size());
        }
        return new ArrayList<>(distinct);
    }

    /** 아직 수집하지 않은 대상만. 이미 완료된 대상을 다시 호출하면 예산을 낭비한다. */
    static List<InfraCollectTarget> pending(List<InfraCollectTarget> allTargets, Set<TargetKey> completed) {
        List<InfraCollectTarget> pending = new ArrayList<>();
        for (InfraCollectTarget target : allTargets) {
            if (!completed.contains(keyOf(target))) {
                pending.add(target);
            }
        }
        return pending;
    }

    /** 기대 대상 중 수집이 끝난 수. 진척 로그에 쓴다. */
    static int collectedCount(List<InfraCollectTarget> allTargets, Set<TargetKey> completed) {
        int done = 0;
        for (InfraCollectTarget target : allTargets) {
            if (completed.contains(keyOf(target))) {
                done++;
            }
        }
        return done;
    }

    /** 기대 대상이 전부 수집됐는가. 이것이 참일 때만 반영한다. */
    static boolean isComplete(List<InfraCollectTarget> allTargets, Set<TargetKey> completed) {
        return !allTargets.isEmpty() && collectedCount(allTargets, completed) == allTargets.size();
    }

    private static TargetKey keyOf(InfraCollectTarget target) {
        return new TargetKey(target.regionCodeValue(), target.industryCodeValue());
    }
}
