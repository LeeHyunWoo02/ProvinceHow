package SDD.smash.domain.job.infrastructure.batch.dto;

import lombok.Builder;
import lombok.Getter;

/**
 * 고용행정통계 Upsert 파라미터.
 *
 * <p><b>필드명이 곧 SQL 네임드 파라미터명</b>이다({@code :sigunguCode}, {@code :statMonth} …).
 * {@code BeanPropertyItemSqlParameterSourceProvider} 가 getter 로 읽으므로 record 가 아니라
 * getter 를 갖는 클래스여야 한다. 필드명과 SQL 중 한쪽만 바꾸면 런타임에 깨진다.
 */
@Getter
@Builder
public class RegionJobStatisticsUpsertRow {

    private String sigunguCode;
    private String jobTopCode;
    private String statMonth;
    private Long jobOpenings;
    private Long jobSeekers;
    private Long placements;
    private Long validOpenings;
    private Long validSeekers;
}
