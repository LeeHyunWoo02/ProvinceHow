package SDD.smash.domain.infra.infrastructure.persistence;

import SDD.smash.domain.infra.domain.model.Major;
import SDD.smash.domain.infra.infrastructure.persistence.projection.IndustryCountRow;
import SDD.smash.domain.infra.infrastructure.persistence.projection.MajorInfraSummaryRow;
import SDD.smash.domain.infra.infrastructure.persistence.projection.RegionMajorScoreRow;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * {@code infra} 테이블 조회. {@code major} 는 {@code industry} 테이블에만 있으므로
 * 세 쿼리 모두 {@code IndustryJpaEntity} 와 명시적으로 {@code JOIN ... ON} 한다
 * (FK 객체 참조를 없앴으므로 As-Is 의 {@code i.industry.major} 같은 암시적 경로는 쓸 수 없다).
 */
public interface InfraJpaRepository extends JpaRepository<InfraJpaEntity, Long> {

    @Query("""
            SELECT new SDD.smash.infra.infrastructure.persistence.projection.MajorInfraSummaryRow(
                ind.major,
                SUM(i.count),
                AVG(i.score)
            )
            FROM InfraJpaEntity i
            JOIN IndustryJpaEntity ind ON ind.code = i.industryCode
            WHERE i.sigunguCode = :sigunguCode
              AND ind.major = :major
            GROUP BY ind.major
            """)
    Optional<MajorInfraSummaryRow> findMajorSummary(@Param("sigunguCode") String sigunguCode,
                                                    @Param("major") Major major);

    @Query("""
            SELECT new SDD.smash.infra.infrastructure.persistence.projection.IndustryCountRow(
                ind.major,
                ind.name,
                i.count,
                i.ratio
            )
            FROM InfraJpaEntity i
            JOIN IndustryJpaEntity ind ON ind.code = i.industryCode
            WHERE i.sigunguCode = :sigunguCode
            """)
    List<IndustryCountRow> findIndustryCounts(@Param("sigunguCode") String sigunguCode);

    @Query("""
            SELECT new SDD.smash.infra.infrastructure.persistence.projection.RegionMajorScoreRow(
                i.sigunguCode,
                ind.major,
                AVG(i.score)
            )
            FROM InfraJpaEntity i
            JOIN IndustryJpaEntity ind ON ind.code = i.industryCode
            WHERE ind.major IN :majors
            GROUP BY i.sigunguCode, ind.major
            """)
    List<RegionMajorScoreRow> findRegionMajorScores(@Param("majors") Collection<Major> majors);
}
