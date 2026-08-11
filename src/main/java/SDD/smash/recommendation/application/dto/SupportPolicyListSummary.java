package SDD.smash.recommendation.application.dto;

import java.util.List;

/** 지원정책 목록. As-Is {@code SupportListDTO} 자리를 대신한다(필드명 {@code supportDTOList} 그대로). */
public record SupportPolicyListSummary(List<SupportPolicyItem> supportDTOList) {
}
