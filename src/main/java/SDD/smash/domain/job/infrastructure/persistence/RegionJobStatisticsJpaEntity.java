package SDD.smash.domain.job.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * {@code region_job_statistics} 테이블 매핑. 시군구 × 직종 대분류 × 기준월의 EIS 고용행정통계.
 *
 * <p><b>인덱스를 하나만 선언한다</b>(persistence-conventions §2.5 — 중복 인덱스는 쓰기 비용만 늘린다).
 * <ul>
 *   <li>{@code (stat_month, job_top_code)} 하나가 최신월 탐색({@code MAX(stat_month)}),
 *       월 전체 조회, 월+직종 조회를 모두 커버한다. {@code stat_month} 단독 인덱스는
 *       이 인덱스의 leftmost prefix 라 중복이므로 붙이지 않는다.</li>
 *   <li>시계열 조회({@code WHERE sigungu_code = ? AND job_top_code = ?})는 유니크 제약
 *       {@code (sigungu_code, job_top_code, stat_month)} 의 prefix 로 커버된다.</li>
 * </ul>
 */
@Entity
@Table(
        name = "region_job_statistics",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_region_job_stat",
                        columnNames = {"sigungu_code", "job_top_code", "stat_month"})
        },
        indexes = {
                @Index(name = "idx_region_job_stat_month_job", columnList = "stat_month, job_top_code")
        }
)
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class RegionJobStatisticsJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sigungu_code", length = 5, nullable = false)
    private String sigunguCode;

    /** 직종 <b>대분류</b> 2자리(01~13). 중분류가 아니다. */
    @Column(name = "job_top_code", length = 2, nullable = false)
    private String jobTopCode;

    /**
     * 기준월. {@code char(7)} 의 {@code 2026-07} 로 둔다 — 고정폭이라 사전순 정렬이 곧 시간순이고
     * ({@code MAX(stat_month)} 가 최신월), CSV·로그·DDL 어디서나 사람이 그대로 읽는다.
     * {@code int}(202607) 로 두면 자릿수가 틀린 값(20267)을 컬럼이 막지 못한다.
     */
    @Column(name = "stat_month", columnDefinition = "char(7)", nullable = false)
    private String statMonth;

    @Column(name = "job_openings", nullable = false)
    private Long jobOpenings;

    @Column(name = "job_seekers", nullable = false)
    private Long jobSeekers;

    @Column(name = "placements", nullable = false)
    private Long placements;

    @Column(name = "valid_openings", nullable = false)
    private Long validOpenings;

    @Column(name = "valid_seekers", nullable = false)
    private Long validSeekers;
}
