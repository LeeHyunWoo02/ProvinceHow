package SDD.smash.recommendation.application.dto;

import SDD.smash.support.application.dto.SupportPolicyView;

/**
 * 지원정책 한 건의 응답 표현.
 *
 * <p><b>필드명이 외부 API 어휘(plcyNm/aplyUrlAddr/plcyKywdNm)를 그대로 쓴다.</b>
 * As-Is 의 {@code SupportDTO} 가 이미 이 이름으로 JSON을 내려주고 있었고, 이 JSON 계약을
 * 프론트가 그대로 소비한다. global-conventions §2.3은 외부 어휘가 {@code infrastructure/external}
 * 밖으로 새면 안 된다고 하지만, 그 규칙은 새 코드의 내부 설계 원칙이고 여기서는 <b>기존
 * 공개 API 계약을 그대로 보존</b>하는 것이 "동작 무변경" 원칙에서 더 우선한다.
 * 계약을 바꾸는 것은 프론트와 합의가 필요한 별도 작업이다.
 */
public record SupportPolicyItem(String plcyNm, String aplyUrlAddr, String plcyKywdNm) {

    public static SupportPolicyItem from(SupportPolicyView view) {
        return new SupportPolicyItem(view.name(), view.applyUrl(), view.keyword());
    }
}
