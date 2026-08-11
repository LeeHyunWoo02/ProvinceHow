package SDD.smash.support.application;

import SDD.smash.address.application.port.in.AddressQueryUseCase;
import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.support.application.dto.SupportPolicyView;
import SDD.smash.support.application.port.in.SupportQueryUseCase;
import SDD.smash.support.domain.model.SupportTag;
import SDD.smash.support.domain.port.SupportPolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 지원정책 조회 유스케이스. As-Is {@code SupportService} 를 옮긴 것이다.
 *
 * <p>RDB 가 없으므로 {@code @Transactional} 이 없다. {@code support} 컨텍스트 전체가
 * 이 컨텍스트에 해당 없음(architecture-conventions §9 5단계 제약)이다.
 *
 * <p><b>지금은 어느 컨트롤러도 이 클래스를 호출하지 않는다.</b> Redis 를 새 네임스페이스
 * ({@code support:policy:*})로 조회하는데, 그 네임스페이스에 실제로 쓰는 것은
 * {@code RefreshSupportPolicyService}(아직 트리거되지 않음)뿐이라 지금 호출해도 항상 비어 있다.
 * `recommendation` 단계에서 옛 스케줄러/서비스를 대체할 때 함께 살아난다.
 */
@Service
@RequiredArgsConstructor
public class SupportQueryService implements SupportQueryUseCase {

    private final AddressQueryUseCase addressQueryUseCase;
    private final SupportPolicyRepository supportPolicyRepository;

    /**
     * As-Is 는 "어느 태그에도 데이터가 없으면 null"을 구분했다. 정본 저장소 포트의
     * {@code countBy} 가 {@code int} 를 돌려주므로 그 구분은 더는 표현할 수 없어
     * 합계를 그대로 돌려준다(데이터가 전혀 없으면 0). 이 메서드가 아직 호출되지 않으므로
     * 지금은 관측 가능한 차이가 없다.
     */
    @Override
    public Integer getAllSupportCount(SigunguCode sigunguCode) {
        addressQueryUseCase.checkSigunguExistsOrThrow(sigunguCode);

        int total = 0;
        for (SupportTag tag : SupportTag.values()) {
            total += supportPolicyRepository.countBy(sigunguCode, tag);
        }
        return total;
    }

    /** As-Is 순서 그대로 — {@code supportChoice} 판정이 시군구 검증보다 먼저다. */
    @Override
    public Integer getFitSupportCount(SigunguCode sigunguCode, Integer supportChoice) {
        if (supportChoice == null || supportChoice == 0) {
            return null;
        }
        addressQueryUseCase.checkSigunguExistsOrThrow(sigunguCode);

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
    @Override
    public List<SupportPolicyView> getAllSupportPolicies(SigunguCode sigunguCode) {
        addressQueryUseCase.checkSigunguExistsOrThrow(sigunguCode);

        List<SupportPolicyView> result = new ArrayList<>();
        for (SupportTag tag : SupportTag.values()) {
            for (var policy : supportPolicyRepository.findBy(sigunguCode, tag)) {
                result.add(SupportPolicyView.from(policy));
            }
        }
        return result;
    }
}
