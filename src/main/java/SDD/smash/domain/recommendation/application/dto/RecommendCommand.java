package SDD.smash.domain.recommendation.application.dto;

import SDD.smash.global.domain.model.Money;
import SDD.smash.domain.dwelling.domain.model.DwellingType;
import SDD.smash.domain.job.domain.model.JobCode;

/**
 * 추천 유스케이스 입력. presentation 경계에서 원시 요청 파라미터를 값 객체로 승격해 채운다.
 *
 * @param supportChoice 사용자가 고른 지원정책 태그의 비트마스크(0~15). 선택 안 함은 null/0
 * @param jobCode 선호 직종 중분류. 선택 안 함은 null
 * @param dwellingType 주거 유형
 * @param budget 예산(만원)
 * @param infraChoice 사용자가 고른 인프라 대분류의 비트마스크(0~15). 선택 안 함은 null/0
 */
public record RecommendCommand(Integer supportChoice, JobCode jobCode, DwellingType dwellingType,
                               Money budget, Integer infraChoice) {
}
