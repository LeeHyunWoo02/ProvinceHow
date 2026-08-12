package SDD.smash.domain.job.infrastructure.persistence.projection;

/**
 * 지역별 일자리 수 프로젝션의 기술 DTO.
 *
 * <p>{@code SUM(int)} 은 {@code Long} 이고 집계 대상이 없으면 {@code null} 이다.
 * 반면 단일 행의 {@code count} 는 {@code Integer} 라서 생성자를 둘 둔다 —
 * As-Is {@code JobCountDTO} 가 같은 이유로 생성자 둘을 갖고 있었다.
 * 도메인 타입으로의 승격과 null → 0 보정은 어댑터가 한다.
 */
public record RegionJobCountRow(String sigunguCode, Long count) {

    public RegionJobCountRow(String sigunguCode, Integer count) {
        this(sigunguCode, count == null ? null : count.longValue());
    }
}
