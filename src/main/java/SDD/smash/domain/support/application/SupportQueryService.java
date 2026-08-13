package SDD.smash.domain.support.application;

import SDD.smash.domain.address.application.AddressQueryService;
import SDD.smash.global.domain.model.SigunguCode;
import SDD.smash.domain.support.application.dto.SupportPolicyView;
import SDD.smash.domain.support.domain.model.SupportTag;
import SDD.smash.domain.support.domain.port.SupportPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 지원정책 조회 유스케이스. {@code recommendation} 이 support 를 호출하는 통로다.
 *
 * <p>RDB 가 없는 컨텍스트라 {@code @Transactional} 이 없다. 정본은 Redis 이며
 * {@code SupportPolicyRepository} 포트 뒤에 있다(redis-conventions §2.2).
 */
@Service
@RequiredArgsConstructor
public class SupportQueryService {

    private final AddressQueryService addressQueryService;
    private final SupportPolicyRepository supportPolicyRepository;

    /**
     * As-Is 는 "어느 태그에도 데이터가 없으면 null"을 구분했다. 정본 저장소 포트의
     * {@code countBy} 가 {@code int} 를 돌려주므로 그 구분은 더는 표현할 수 없어
     * 합계를 그대로 돌려준다(데이터가 전혀 없으면 0). 이 메서드가 아직 호출되지 않으므로
     * 지금은 관측 가능한 차이가 없다.
     */
    public Integer getAllSupportCount(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        int total = 0;
        for (SupportTag tag : SupportTag.values()) {
            total += supportPolicyRepository.countBy(sigunguCode, tag);
        }
        return total;
    }

    /** As-Is 순서 그대로 — {@code supportChoice} 판정이 시군구 검증보다 먼저다. */
    public Integer getFitSupportCount(SigunguCode sigunguCode, Integer supportChoice) {
        if (supportChoice == null || supportChoice == 0) {
            return null;
        }
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        int sum = 0;
        for (SupportTag tag : SupportTag.fromChoiceMask(supportChoice)) {
            sum += supportPolicyRepository.countBy(sigunguCode, tag);
        }
        return sum;
    }

    /**
     * As-Is 는 어느 태그에도 데이터가 없으면 {@code null} 을 돌려줬다. {@code findBy} 가
     * 빈 목록을 돌려주는 정본 저장소 포트라 여기서는 빈 목록으로 대신한다.
     */
    public List<SupportPolicyView> getAllSupportPolicies(SigunguCode sigunguCode) {
        addressQueryService.checkSigunguExistsOrThrow(sigunguCode);

        List<SupportPolicyView> result = new ArrayList<>();
        for (SupportTag tag : SupportTag.values()) {
            for (var policy : supportPolicyRepository.findBy(sigunguCode, tag)) {
                result.add(SupportPolicyView.from(policy));
            }
        }
        return result;
    }
}
