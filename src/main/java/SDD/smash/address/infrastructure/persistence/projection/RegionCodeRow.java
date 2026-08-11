package SDD.smash.address.infrastructure.persistence.projection;

/**
 * 시도-시군구 조인 프로젝션의 기술 DTO.
 *
 * <p>JPQL {@code new} 로 직접 도메인 타입을 만들지 않는다.
 * 값 객체 생성자 검증이 쿼리 실행 도중 터지면 원인을 추적하기 어렵기 때문이다.
 * 도메인 타입으로의 승격은 어댑터가 한다.
 */
public record RegionCodeRow(String sidoCode, String sidoName,
                            String sigunguCode, String sigunguName) {
}
