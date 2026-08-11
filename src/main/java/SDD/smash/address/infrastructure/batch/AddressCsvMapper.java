package SDD.smash.address.infrastructure.batch;

import SDD.smash.address.infrastructure.batch.dto.SidoCsvRow;
import SDD.smash.address.infrastructure.batch.dto.SigunguCsvRow;
import SDD.smash.address.infrastructure.persistence.SidoJpaEntity;
import SDD.smash.address.infrastructure.persistence.SigunguJpaEntity;

import static SDD.smash.common.util.BatchTextUtil.normalize;

/**
 * CSV 행 → JPA 엔티티 변환. As-Is {@code AddressConverter} 를 옮긴 것이다.
 *
 * <p>대량 적재 배치는 Aggregate 를 거치지 않는다(architecture-conventions §6.2).
 * 정제(normalize)는 여기서 끝내고 도메인은 이미 정제된 값만 본다.
 */
public final class AddressCsvMapper {

    private AddressCsvMapper() {
    }

    public static SidoJpaEntity toSidoJpaEntity(SidoCsvRow row) {
        return SidoJpaEntity.builder()
                .sidoCode(normalize(row.sidoCode()))
                .name(normalize(row.name()))
                .build();
    }

    /**
     * As-Is 는 조회해 온 {@code Sido} 엔티티를 그대로 물렸다. 그 엔티티의 코드를 값으로 넣는 것과 같다.
     * 시도를 찾지 못했을 때 {@code null} 을 넣는 것도 As-Is 그대로다
     * ({@code sido_code} 가 not null 이라 flush 시점에 실패한다).
     */
    public static SigunguJpaEntity toSigunguJpaEntity(SigunguCsvRow row, String resolvedSidoCode) {
        return SigunguJpaEntity.builder()
                .sigunguCode(normalize(row.sigunguCode()))
                .name(normalize(row.name()))
                .sidoCode(resolvedSidoCode)
                .build();
    }
}
