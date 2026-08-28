package SDD.smash.global.batch.launch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 배치 재실행 판정기. Spring Batch 메타 테이블(meta DB)을 직접 조회한다.
 *
 * <p><b>왜 {@code JobExplorer} 가 아니라 SQL 인가</b>
 * <ul>
 *   <li>구버전은 {@code jobExplorer.getJobInstances(jobName, 0, 20)} 로 <b>최근 20개 JobInstance</b> 만 봤다.
 *       기준일/기준월이 파라미터로 들어가면 인스턴스가 날마다 늘어나 20개를 금방 벗어나고,
 *       "이미 돌린 기준월"을 다시 도는 오판이 생긴다. 조회 한계를 없애는 것이 이 클래스의 존재 이유다.</li>
 *   <li>Step 단위 판정({@link #stepAlreadyCompleted})은 JobExplorer 로 하면
 *       인스턴스 수 × 실행 수만큼 왕복 조회가 필요하다. SQL 이면 조인 한 번이다.</li>
 * </ul>
 *
 * <p><b>판정 불가는 fail-open 이다.</b> 메타 테이블이 아직 없거나 조회가 실패하면
 * {@code false}(= 아직 안 돌았다)를 돌려주고 경고만 남긴다. 적재 배치는 전부 upsert 이거나
 * 자연키 merge 라 한 번 더 도는 쪽이 안 도는 쪽보다 안전하다.
 *
 * <p>Spring Batch 5 메타 스키마 기준이다 —
 * {@code BATCH_JOB_EXECUTION_PARAMS(PARAMETER_NAME, PARAMETER_VALUE)}.
 */
@Component
@Slf4j
public class BatchGuard {

    /** 프로퍼티로 들어오는 테이블 접두어를 SQL 에 문자열로 끼워 넣으므로 형식을 강제한다. */
    private static final Pattern SAFE_PREFIX = Pattern.compile("[A-Za-z0-9_]*");

    private final NamedParameterJdbcTemplate metaJdbcTemplate;
    private final String tablePrefix;

    public BatchGuard(@Qualifier("batchDataSource") DataSource metaDataSource,
                      @Value("${spring.batch.jdbc.table-prefix:BATCH_}") String tablePrefix) {
        if (!SAFE_PREFIX.matcher(tablePrefix).matches()) {
            throw new IllegalArgumentException("허용되지 않는 배치 테이블 접두어: " + tablePrefix);
        }
        this.metaJdbcTemplate = new NamedParameterJdbcTemplate(metaDataSource);
        this.tablePrefix = tablePrefix;
    }

    /**
     * 같은 jobName 이 같은 seedVersion 으로 COMPLETED 된 적이 있으면 true.
     *
     * <p>구버전과 판정 의미는 같고 조회 한계(20개)만 사라졌다.
     */
    public boolean alreadyDone(String jobName, String seedVersion) {
        return jobAlreadyCompleted(jobName, Map.of("seedVersion", seedVersion));
    }

    /**
     * jobName 이 주어진 파라미터를 <b>전부</b> 가진 채 COMPLETED 된 적이 있으면 true.
     *
     * <p>seedMasterJob 을 기동할 때 {@code JobInstanceAlreadyCompleteException} 이 터지기 전에
     * 미리 판정해 "왜 건너뛰는지"를 로그로 남기기 위한 용도다.
     */
    public boolean jobAlreadyCompleted(String jobName, Map<String, String> jobParameters) {
        if (jobParameters == null || jobParameters.isEmpty()) {
            return false;
        }

        MapSqlParameterSource params = new MapSqlParameterSource("jobName", jobName);
        List<String> exists = new ArrayList<>();
        int index = 0;
        for (Map.Entry<String, String> entry : jobParameters.entrySet()) {
            String nameKey = "pn" + index;
            String valueKey = "pv" + index;
            params.addValue(nameKey, entry.getKey());
            params.addValue(valueKey, entry.getValue());
            exists.add("""
                    AND EXISTS (SELECT 1 FROM %sJOB_EXECUTION_PARAMS p
                                 WHERE p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                                   AND p.PARAMETER_NAME = :%s
                                   AND p.PARAMETER_VALUE = :%s)
                    """.formatted(tablePrefix, nameKey, valueKey));
            index++;
        }

        String sql = """
                SELECT COUNT(*)
                  FROM %sJOB_EXECUTION je
                  JOIN %sJOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
                 WHERE ji.JOB_NAME = :jobName
                   AND je.STATUS = 'COMPLETED'
                """.formatted(tablePrefix, tablePrefix) + String.join("", exists);

        return count(sql, params) > 0;
    }

    /**
     * stepName 이 <b>어느 Job 에서든</b> 주어진 기준 파라미터 값으로 COMPLETED 된 적이 있으면 true.
     *
     * <p>기준 파라미터는 필수 기준 데이터면 {@code seedVersion},
     * 외부 갱신 데이터면 {@code baseMonth}(yyyyMM) 또는 {@code baseDate}(yyyy-MM-dd)다.
     * Job 이름을 조건에 넣지 않으므로 seedMasterJob 이 돌린 Step 과 정기 스케줄이 돌린
     * 같은 이름의 Step 이 서로의 실행을 인정한다 — 같은 기준월을 두 경로가 중복 적재하지 않는다.
     */
    public boolean stepAlreadyCompleted(String stepName, String parameterName, String parameterValue) {
        if (stepName == null || parameterName == null || parameterValue == null || parameterValue.isBlank()) {
            return false;
        }

        String sql = """
                SELECT COUNT(*)
                  FROM %sSTEP_EXECUTION se
                  JOIN %sJOB_EXECUTION je ON je.JOB_EXECUTION_ID = se.JOB_EXECUTION_ID
                  JOIN %sJOB_EXECUTION_PARAMS p ON p.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID
                 WHERE se.STEP_NAME = :stepName
                   AND se.STATUS = 'COMPLETED'
                   AND p.PARAMETER_NAME = :parameterName
                   AND p.PARAMETER_VALUE = :parameterValue
                """.formatted(tablePrefix, tablePrefix, tablePrefix);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("stepName", stepName);
        params.put("parameterName", parameterName);
        params.put("parameterValue", parameterValue);

        return count(sql, new MapSqlParameterSource(params)) > 0;
    }

    /**
     * 아직 끝나지 않은({@code END_TIME IS NULL}) 실행 전부.
     *
     * <p>하트비트로 {@code MAX(STEP_EXECUTION.LAST_UPDATED)} 를 함께 가져온다. Job 행의
     * {@code LAST_UPDATED} 는 시작/종료 때만 갱신되므로 그것으로 나이를 재면 정상적으로 몇 시간
     * 도는 배치가 고아로 오판된다. 스텝이 아직 하나도 없으면 {@code CREATE_TIME} 이 기준이다.
     *
     * <p>조회 실패는 다른 판정과 같은 fail-open 이다 — 빈 목록을 돌려주고 아무것도 정리하지 않는다.
     */
    public List<RunningJobExecution> findRunningExecutions() {
        String sql = """
                SELECT je.JOB_EXECUTION_ID AS JOB_EXECUTION_ID,
                       ji.JOB_NAME AS JOB_NAME,
                       COALESCE(je.START_TIME, je.CREATE_TIME) AS STARTED_AT,
                       COALESCE((SELECT MAX(se.LAST_UPDATED)
                                   FROM %sSTEP_EXECUTION se
                                  WHERE se.JOB_EXECUTION_ID = je.JOB_EXECUTION_ID),
                                je.CREATE_TIME) AS HEARTBEAT_AT
                  FROM %sJOB_EXECUTION je
                  JOIN %sJOB_INSTANCE ji ON ji.JOB_INSTANCE_ID = je.JOB_INSTANCE_ID
                 WHERE je.END_TIME IS NULL
                """.formatted(tablePrefix, tablePrefix, tablePrefix);

        try {
            return metaJdbcTemplate.query(sql, new MapSqlParameterSource(), (rs, rowNum) ->
                    new RunningJobExecution(
                            rs.getLong("JOB_EXECUTION_ID"),
                            rs.getString("JOB_NAME"),
                            toLocalDateTime(rs.getTimestamp("STARTED_AT")),
                            toLocalDateTime(rs.getTimestamp("HEARTBEAT_AT"))));
        } catch (DataAccessException e) {
            log.warn("[batch] 진행 중 실행 조회 실패 - 정리 대상이 없는 것으로 간주한다. reason={}", e.getMessage());
            return List.of();
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private long count(String sql, MapSqlParameterSource params) {
        try {
            Long found = metaJdbcTemplate.queryForObject(sql, params, Long.class);
            return found == null ? 0L : found;
        } catch (DataAccessException e) {
            log.warn("[batch] 배치 이력 조회 실패 - 아직 실행되지 않은 것으로 간주한다. reason={}", e.getMessage());
            return 0L;
        }
    }
}
