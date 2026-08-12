package SDD.smash.domain.recommendation.presentation.dto;

import SDD.smash.domain.recommendation.application.dto.CodeItem;

/** 코드-이름 목록 응답 한 건. As-Is {@code CodeDTO} 자리를 대신한다. */
public record CodeResponse(String code, String name) {

    public static CodeResponse from(CodeItem item) {
        return new CodeResponse(item.code(), item.name());
    }
}
