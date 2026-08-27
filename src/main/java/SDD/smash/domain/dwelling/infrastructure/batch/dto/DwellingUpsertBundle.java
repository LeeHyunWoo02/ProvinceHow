package SDD.smash.domain.dwelling.infrastructure.batch.dto;

import java.util.List;

/**
 * 한 시군구 처리 결과 — 두 테이블에 나눠 쓸 행들을 함께 들고 다닌다.
 *
 * <p>통합 중앙값은 유형별 중앙값으로 재구성할 수 없어(중앙값은 결합법칙이 없다)
 * 한 Processor 가 3종을 모두 처리한다. 그 결과가 두 테이블로 갈리므로 번들이 필요하다.
 *
 * @param combined {@code dwelling} 행. 3종 원시 레코드를 풀링해 계산한다. 값이 없으면 null
 * @param byType   {@code dwelling_by_type} 행들. 수집에 성공하고 값이 있는 유형만 담긴다
 */
public record DwellingUpsertBundle(DwellingUpsertRow combined, List<DwellingByTypeUpsertRow> byType) {

    public DwellingUpsertBundle {
        byType = (byType == null) ? List.of() : List.copyOf(byType);
    }
}
