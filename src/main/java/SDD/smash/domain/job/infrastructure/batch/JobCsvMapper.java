package SDD.smash.domain.job.infrastructure.batch;

import SDD.smash.domain.job.infrastructure.batch.dto.JobCodeMiddleCsvRow;
import SDD.smash.domain.job.infrastructure.batch.dto.JobCodeTopCsvRow;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeMiddleJpaEntity;
import SDD.smash.domain.job.infrastructure.persistence.JobCodeTopJpaEntity;

import static SDD.smash.global.util.BatchTextUtil.addLeadingZero;
import static SDD.smash.global.util.BatchTextUtil.addLeadingZeroThird;
import static SDD.smash.global.util.BatchTextUtil.normalize;

/**
 * CSV 행 → JPA 엔티티 변환. As-Is {@code JobConverter} 를 옮긴 것이다.
 *
 * <p>대분류는 2자리, 중분류는 3자리로 앞을 0 으로 채운다. 이 정규화는 배치 안에서 끝나고
 * 도메인은 이미 정규화된 코드만 본다.
 */
public final class JobCsvMapper {

    private JobCsvMapper() {
    }

    public static JobCodeTopJpaEntity toTopJpaEntity(JobCodeTopCsvRow row) {
        return JobCodeTopJpaEntity.builder()
                .code(addLeadingZero(normalize(row.code())))
                .name(normalize(row.name()))
                .build();
    }

    /**
     * As-Is 는 조회해 온 {@code JobCodeTop} 엔티티를 그대로 물렸다.
     * 그 엔티티의 코드를 값으로 넣는 것과 같다.
     */
    public static JobCodeMiddleJpaEntity toMiddleJpaEntity(JobCodeMiddleCsvRow row, String resolvedTopCode) {
        return JobCodeMiddleJpaEntity.builder()
                .code(addLeadingZeroThird(normalize(row.code())))
                .name(normalize(row.name()))
                .topCode(resolvedTopCode)
                .build();
    }
}
