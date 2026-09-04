package SDD.smash.domain.support.application.dto;

/**
 * 지원정책 태그 카탈로그 한 건. 다른 컨텍스트가 support 의 도메인 enum({@code SupportTag})을
 * 직접 참조하지 않도록 application 경계에서 노출하는 조회 결과다.
 *
 * @param code enum 상수명({@code SupportTag.name()})
 * @param name 한국어 라벨({@code SupportTag.getValue()})
 */
public record SupportTagView(String code, String name) {
}
