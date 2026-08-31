package SDD.smash.domain.job.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface RegionJobStatisticsJpaRepository extends JpaRepository<RegionJobStatisticsJpaEntity, Long> {

    /** 고정폭 {@code char(7)} 이라 문자열 MAX 가 곧 최신월이다. 행이 하나도 없으면 비어 있다. */
    @Query("SELECT MAX(e.statMonth) FROM RegionJobStatisticsJpaEntity e")
    Optional<String> findLatestStatMonth();

    List<RegionJobStatisticsJpaEntity> findAllByStatMonth(String statMonth);

    List<RegionJobStatisticsJpaEntity> findAllByStatMonthAndJobTopCode(String statMonth, String jobTopCode);

    List<RegionJobStatisticsJpaEntity> findAllByStatMonthAndSigunguCode(String statMonth, String sigunguCode);

    Optional<RegionJobStatisticsJpaEntity> findBySigunguCodeAndJobTopCodeAndStatMonth(
            String sigunguCode, String jobTopCode, String statMonth);

    List<RegionJobStatisticsJpaEntity> findAllBySigunguCodeAndJobTopCodeOrderByStatMonthAsc(
            String sigunguCode, String jobTopCode);

    List<RegionJobStatisticsJpaEntity> findAllBySigunguCode(String sigunguCode);
}
