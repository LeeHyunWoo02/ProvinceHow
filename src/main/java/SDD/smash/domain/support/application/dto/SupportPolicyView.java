package SDD.smash.domain.support.application.dto;

import SDD.smash.domain.support.domain.model.SupportPolicy;

/** 지원정책 한 건의 조회 결과. As-Is {@code SupportDTO} 자리를 대신한다. */
public record SupportPolicyView(String name, String applyUrl, String keyword) {

    public static SupportPolicyView from(SupportPolicy policy) {
        return new SupportPolicyView(policy.name(), policy.applyUrl(), policy.keyword());
    }
}
