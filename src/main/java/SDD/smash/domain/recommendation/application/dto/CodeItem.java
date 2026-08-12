package SDD.smash.domain.recommendation.application.dto;

/** 코드-이름 쌍. 대분류/중분류/시도/시군구/지원태그 목록 조회가 전부 이 모양이다. */
public record CodeItem(String code, String name) {
}
