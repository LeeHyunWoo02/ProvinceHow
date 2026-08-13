package SDD.smash.global.batch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.util.Set;

/**
 * 외부 갱신 Step 을 돌리기 전에 <b>선행 기준 데이터가 실제로 적재돼 있는지</b> 확인한다.
 *
 * <p>배치 메타에 "sigunguStep COMPLETED" 이력이 있어도 그 뒤 data DB 볼륨을 지웠으면
 * 테이블은 비어 있다. 그 상태로 외부 배치를 돌리면 Processor 가 FK 미매칭으로 전 행을 조용히
 * skip 해서 <b>성공했는데 데이터가 없는</b> 결과가 나온다. 그래서 이력이 아니라 행 존재를 본다.
 *
 * <p>테이블명이 {@code global} 에 들어오는 것은 의도적인 타협이다.
 * seedMasterJob 은 여러 컨텍스트를 조립하는 부트스트랩 지점이고
 * ({@code DataDBConfig} 가 컨텍스트별 persistence 패키지를 문자열로 열거하는 것과 같은 성격),
 * 컨텍스트의 도메인·엔티티를 import 하지 않기 위해 <b>테이블 이름 문자열</b>만 안다.
 * 허용 목록({@link #ALLOWED_TABLES})을 벗어난 이름은 조회하지 않는다.
 */
@Component
@Slf4j
public class SeedDataPrerequisiteInspector {

    public static final String SIDO = "sido";
    public static final String SIGUNGU = "sigungu";
    public static final String INDUSTRY = "industry";
    public static final String JOB_CODE_TOP = "job_code_top";
    public static final String JOB_CODE_MIDDLE = "job_code_middle";

    private static final Set<String> ALLOWED_TABLES =
            Set.of(SIDO, SIGUNGU, INDUSTRY, JOB_CODE_TOP, JOB_CODE_MIDDLE);

    private final JdbcTemplate dataJdbcTemplate;

    public SeedDataPrerequisiteInspector(@Qualifier("dataDBSource") DataSource dataDataSource) {
        this.dataJdbcTemplate = new JdbcTemplate(dataDataSource);
    }

    /**
     * 테이블에 행이 하나라도 있으면 true.
     *
     * <p>테이블이 없거나 조회가 실패하면 <b>false</b>(= 선행 데이터 없음)로 본다.
     * 여기서는 fail-open 이 아니라 fail-closed 다 — 판정을 못 하면 외부 배치를 돌리지 않는 편이 안전하다.
     */
    public boolean hasRows(String table) {
        if (!ALLOWED_TABLES.contains(table)) {
            throw new IllegalArgumentException("선행 데이터 확인이 허용되지 않은 테이블: " + table);
        }
        try {
            Integer found = dataJdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM " + table + ")", Integer.class);
            return found != null && found == 1;
        } catch (DataAccessException e) {
            log.warn("[batch] 선행 데이터 확인 실패 table={} - 데이터 없음으로 간주한다. reason={}",
                    table, e.getMessage());
            return false;
        }
    }
}
