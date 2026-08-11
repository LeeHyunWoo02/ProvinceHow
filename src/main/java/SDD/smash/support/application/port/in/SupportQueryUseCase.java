package SDD.smash.support.application.port.in;

import SDD.smash.common.domain.model.SigunguCode;
import SDD.smash.support.application.dto.SupportPolicyView;

import java.util.List;

/** 지원정책 조회 in-port. {@code recommendation} 이 support 를 호출하는 통로다. */
public interface SupportQueryUseCase {

    /**
     * 해당 시군구의 전체 태그 지원정책 개수 합.
     *
     * <p>As-Is 는 "어느 태그에도 데이터가 없으면 {@code null}"을 구분했으나,
     * {@code SupportPolicyRepository.countBy} 가 {@code int} 를 돌려주는 정본 저장소 포트라
     * "0건"과 "데이터 없음"을 더는 구분하지 못한다(보고에 명시한 설계상 단순화).
     */
    Integer getAllSupportCount(SigunguCode sigunguCode);

    /** {@code supportChoice} 가 {@code null} 이거나 0이면 {@code null} 이다(시군구 검증보다 먼저 판정). */
    Integer getFitSupportCount(SigunguCode sigunguCode, Integer supportChoice);

    /** 해당 시군구의 전체 태그 지원정책 목록. 적재된 것이 없으면 빈 목록이다. */
    List<SupportPolicyView> getAllSupportPolicies(SigunguCode sigunguCode);
}
