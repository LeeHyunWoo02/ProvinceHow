package SDD.smash.domain.infra.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code infra_collection_target} — 수집 <b>대상 진행</b> 체크포인트.
 *
 * <p>한 행 = "이 회차에서 (개방자치단체, 업종) 대상 하나를 <b>끝까지</b> 수집했다".
 * LOCALDATA API 는 하루 예산(9,000회)으로 전국(약 19,800회)을 다 받을 수 없어 수집이
 * 여러 날에 걸친다. 어디까지 했는지를 남겨야 다음 실행이 이어받고, 이미 받은 대상을
 * 다시 호출해 예산을 낭비하지 않는다.
 *
 * <h2>결과가 0건인 대상도 행을 남긴다</h2>
 * "완성 여부"를 <b>행 수</b>로 판정하기 때문이다. 시설이 없는 대상을 미기록으로 두면
 * 그 회차는 영원히 완성되지 않는다.
 *
 * <h2>{@code run_key} — 수집 회차</h2>
 * {@code baseDate} 를 쓰면 날짜가 바뀌는 순간 이어받기가 끊긴다. 회차를 따로 두고
 * 미완성 회차가 있으면 그 키를 이어받는다. 값은 회차 <b>시작일</b>({@code yyyy-MM-dd})이다.
 *
 * <p>이 행과 그 대상이 만든 {@link InfraStagingCountJpaEntity} 행은 <b>같은 트랜잭션에서</b>
 * 커밋된다. 카운트만 남고 대상 진행이 없으면 재수집 때 이중 합산이 된다.
 */
@Entity
@Table(
        name = "infra_collection_target",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_infra_collection_target",
                        columnNames = {"run_key", "open_org_code", "industry_code"})
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class InfraCollectionTargetJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 수집 회차 키. 회차 시작일({@code yyyy-MM-dd}). */
    @Column(name = "run_key", length = 20, nullable = false)
    private String runKey;

    /** 개방자치단체코드. {@code 6110000_ALL} 같은 집합 코드도 있어 7자리보다 넉넉히 잡는다. */
    @Column(name = "open_org_code", length = 16, nullable = false)
    private String openOrgCode;

    @Column(name = "industry_code", length = 10, nullable = false)
    private String industryCode;

    /** 이 대상에서 집계된 영업중 사업장 수. 0 도 정상이다. 진척 로그·검산용이다. */
    @Column(name = "facility_count", nullable = false)
    private Integer facilityCount;

    /** 이 대상이 쓴 외부 호출 수. 예산 실측치를 남긴다. */
    @Column(name = "api_calls", nullable = false)
    private Integer apiCalls;

    @Column(name = "collected_at", nullable = false)
    private LocalDateTime collectedAt;
}
