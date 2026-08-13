package SDD.smash.domain.job.infrastructure.persistence;

import SDD.smash.domain.job.infrastructure.persistence.projection.JobCountKeyRow;
import SDD.smash.domain.job.infrastructure.persistence.projection.RegionJobCountRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JobCountJpaRepository extends JpaRepository<JobCountJpaEntity, Long> {

    /** FK 객체 참조를 없앴으므로 As-Is 의 {@code j.sigungu.sigunguCode} 가 그냥 {@code j.sigunguCode} 다. */
    @Query("""
            SELECT new SDD.smash.domain.job.infrastructure.persistence.projection.RegionJobCountRow(
                j.sigunguCode,
                SUM(j.count)
            )
            FROM JobCountJpaEntity j
            GROUP BY j.sigunguCode
            """)
    List<RegionJobCountRow> findAllRegionTotals();

    @Query("""
            SELECT new SDD.smash.domain.job.infrastructure.persistence.projection.RegionJobCountRow(
                j.sigunguCode,
                j.count
            )
            FROM JobCountJpaEntity j
            WHERE j.jobCodeMiddleCode = :jobCode
            """)
    List<RegionJobCountRow> findAllRegionCountsOf(@Param("jobCode") String jobCode);

    /**
     * 집계 조회. 대상 행이 없으면 {@code SUM} 이 {@code null} 인 한 행이 나온다.
     * As-Is 도 이 값을 0 으로 바꿔 담아 "일자리 0개"로 응답했다.
     */
    @Query("""
            SELECT SUM(j.count)
            FROM JobCountJpaEntity j
            WHERE j.sigunguCode = :sigunguCode
            """)
    Long sumCountBySigunguCode(@Param("sigunguCode") String sigunguCode);

    /**
     * 이미 적재된 (시군구, 직종중분류) 키 전량.
     *
     * <p>스냅샷 교체 배치가 "이번 스냅샷에서 사라진 조합"을 0 으로 내리는 데 쓴다.
     */
    @Query("""
            SELECT new SDD.smash.domain.job.infrastructure.persistence.projection.JobCountKeyRow(
                j.sigunguCode,
                j.jobCodeMiddleCode
            )
            FROM JobCountJpaEntity j
            """)
    List<JobCountKeyRow> findAllKeys();

    /** 단일 행 조회. 적재된 행이 없으면 비어 있다. */
    @Query("""
            SELECT j.count
            FROM JobCountJpaEntity j
            WHERE j.sigunguCode = :sigunguCode
            AND j.jobCodeMiddleCode = :jobCode
            """)
    Optional<Integer> findCountBySigunguCodeAndJobCode(@Param("sigunguCode") String sigunguCode,
                                                       @Param("jobCode") String jobCode);
}
