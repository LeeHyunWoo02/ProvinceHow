package SDD.smash.domain.job.infrastructure.persistence.projection;

/**
 * {@code JobCount} 에 이미 적재돼 있는 (시군구, 직종중분류) 키.
 *
 * <p>스냅샷 교체 배치가 "지난 스냅샷에는 있었는데 이번에는 사라진 조합"을 찾는 데만 쓴다.
 * 개수 컬럼은 읽지 않는다 — 필요 없고, 행 수가 3만에 가까워질 수 있어 가볍게 가져온다.
 */
public record JobCountKeyRow(String sigunguCode, String jobCodeMiddleCode) {
}
