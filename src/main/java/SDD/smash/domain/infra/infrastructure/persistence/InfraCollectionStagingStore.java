package SDD.smash.domain.infra.infrastructure.persistence;

import SDD.smash.domain.infra.domain.model.IndustryCode;
import SDD.smash.domain.infra.domain.model.RegionIndustryCount;
import SDD.smash.global.domain.model.SigunguCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * LOCALDATA 수집 체크포인트(staging) 접근. {@code infraCollectStep} 과 {@code infraStep} 만 쓴다.
 *
 * <h2>왜 포트가 아닌가</h2>
 * "수집 회차"와 "대상 진행"은 인프라 도메인의 개념이 아니라 <b>배치가 하루치씩 나눠 받기 위한
 * 진행 상태</b>다. 도메인 포트로 올리면 존재하지 않는 도메인 개념({@code CollectionRunKey})을
 * 발명하게 된다. 그래서 같은 컨텍스트의 {@code infrastructure} 안에 두고 배치에서만 쓴다
 * (persistence-conventions §7 — 대량 적재 배치는 Aggregate 를 거치지 않아도 된다).
 *
 * <h2>왜 JDBC 인가</h2>
 * 쓰기가 <b>합산 upsert</b>({@code count = count + VALUES(count)})다. JPA 로는 select-then-update 가
 * 되어 왕복이 늘고 경합에 약하다. 스키마의 정본은 {@link InfraCollectionTargetJpaEntity} /
 * {@link InfraStagingCountJpaEntity} 이고({@code hbm2ddl.auto=update}), 여기서는 그 테이블을 쓴다.
 *
 * <p>트랜잭션 경계를 스스로 열지 않는다. 청크 트랜잭션({@code dataTransactionManager})에 참여해
 * <b>대상 진행 행과 카운트 행이 함께 커밋</b>되게 한다. 중간에 끊겨 카운트만 남으면
 * 재수집 때 이중 합산이 된다.
 */
@Component
@Slf4j
public class InfraCollectionStagingStore {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public InfraCollectionStagingStore(@Qualifier("dataDBSource") DataSource dataDataSource) {
        this.jdbcTemplate = new NamedParameterJdbcTemplate(dataDataSource);
    }

    /** staging 에 남아 있는 회차 키. 오래된 것부터. */
    public List<String> runKeys() {
        return jdbcTemplate.queryForList(
                "SELECT DISTINCT run_key FROM infra_collection_target ORDER BY run_key",
                new MapSqlParameterSource(), String.class);
    }

    /** 이 회차에서 <b>이미 수집을 마친</b> 대상. 다시 호출하지 않기 위한 목록이다. */
    public Set<TargetKey> completedTargets(String runKey) {
        List<TargetKey> rows = jdbcTemplate.query(
                "SELECT open_org_code, industry_code FROM infra_collection_target WHERE run_key = :runKey",
                new MapSqlParameterSource("runKey", runKey),
                (rs, rowNum) -> new TargetKey(rs.getString(1), rs.getString(2)));
        return new LinkedHashSet<>(rows);
    }

    /**
     * 수집을 마친 대상들을 저장한다. <b>카운트 → 대상 진행</b> 순서로 같은 트랜잭션에서 쓴다.
     *
     * @param rows 대상 하나당 (시군구 → 개수) 결과
     */
    public void save(String runKey, List<StagedTarget> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }

        List<SqlParameterSource> countParams = new ArrayList<>();
        for (StagedTarget row : rows) {
            for (Map.Entry<SigunguCode, Integer> entry : row.counts().entrySet()) {
                countParams.add(new MapSqlParameterSource()
                        .addValue("runKey", runKey)
                        .addValue("sigunguCode", entry.getKey().value())
                        .addValue("industryCode", row.industryCode())
                        .addValue("facilityCount", entry.getValue()));
            }
        }
        if (!countParams.isEmpty()) {
            // 한 (시군구, 업종) 에 여러 대상이 기여하므로 덮어쓰기가 아니라 합산이다.
            jdbcTemplate.batchUpdate("""
                    INSERT INTO infra_staging_count (run_key, sigungu_code, industry_code, facility_count)
                    VALUES (:runKey, :sigunguCode, :industryCode, :facilityCount)
                    ON DUPLICATE KEY UPDATE facility_count = facility_count + VALUES(facility_count)
                    """, countParams.toArray(new SqlParameterSource[0]));
        }

        LocalDateTime now = LocalDateTime.now();
        List<SqlParameterSource> targetParams = new ArrayList<>(rows.size());
        for (StagedTarget row : rows) {
            targetParams.add(new MapSqlParameterSource()
                    .addValue("runKey", runKey)
                    .addValue("openOrgCode", row.openOrgCode())
                    .addValue("industryCode", row.industryCode())
                    .addValue("facilityCount", row.facilityCount())
                    .addValue("apiCalls", row.apiCalls())
                    .addValue("collectedAt", now));
        }
        // 진행 행은 대상당 한 번뿐이라 덮어쓰기다. 재실행 시 같은 대상이 다시 오지 않지만,
        // 만약 온다면 카운트가 이미 합산된 뒤이므로 여기서 막지 말고 흔적을 갱신한다.
        jdbcTemplate.batchUpdate("""
                INSERT INTO infra_collection_target
                    (run_key, open_org_code, industry_code, facility_count, api_calls, collected_at)
                VALUES (:runKey, :openOrgCode, :industryCode, :facilityCount, :apiCalls, :collectedAt)
                ON DUPLICATE KEY UPDATE
                    facility_count = VALUES(facility_count),
                    api_calls      = VALUES(api_calls),
                    collected_at   = VALUES(collected_at)
                """, targetParams.toArray(new SqlParameterSource[0]));
    }

    /** 이 회차에 모인 (시군구, 업종) 개수 전체. 완성 판정을 통과한 뒤에만 읽는다. */
    public List<RegionIndustryCount> counts(String runKey) {
        return jdbcTemplate.query("""
                SELECT sigungu_code, industry_code, facility_count
                FROM infra_staging_count
                WHERE run_key = :runKey
                ORDER BY sigungu_code, industry_code
                """,
                new MapSqlParameterSource("runKey", runKey),
                (rs, rowNum) -> new RegionIndustryCount(
                        SigunguCode.of(rs.getString(1)),
                        IndustryCode.of(rs.getString(2)),
                        rs.getInt(3)));
    }

    /** 반영이 끝난 회차를 지운다. 다음 실행은 자연히 새 회차로 시작한다. */
    public void purge(String runKey) {
        MapSqlParameterSource params = new MapSqlParameterSource("runKey", runKey);
        int counts = jdbcTemplate.update("DELETE FROM infra_staging_count WHERE run_key = :runKey", params);
        int targets = jdbcTemplate.update("DELETE FROM infra_collection_target WHERE run_key = :runKey", params);
        log.info("[infraJob] staging 정리 runKey={}, targets={}, counts={}", runKey, targets, counts);
    }

    /** 대상 하나의 식별자. */
    public record TargetKey(String openOrgCode, String industryCode) {
    }

    /**
     * 저장 단위. 대상 하나와 그 대상이 만든 시군구별 개수다.
     *
     * @param counts 시군구 → 개수. 일반구 재분배가 끝난 코드만 들어 있다. 비어 있어도 된다(0건 대상)
     */
    public record StagedTarget(String openOrgCode, String industryCode,
                               Map<SigunguCode, Integer> counts,
                               int facilityCount, int apiCalls) {
    }
}
