-- =============================================================================
-- DDD 헥사고날 전환 마무리 — 옛 FK 인덱스를 엔티티에 선언된 이름으로 개명
--
-- 배경
--   persistence-conventions §2.5: FK 객체 참조를 제거하면 Hibernate 가 조인 컬럼
--   인덱스를 자동 생성하지 않는다. 그래서 아래 3개 컬럼에 @Index 를 명시했다.
--
--     sigungu.sido_code                → idx_sigungu_sido
--     job_code_middle.top_code         → idx_job_code_middle_top
--     JobCount.job_code_middle_code    → idx_jobcount_job_code_middle
--
--   문제는 **기존 DB 에는 옛 FK 가 남긴 인덱스가 같은 컬럼에 이미 있다**는 것이다.
--   그 인덱스 이름은 Hibernate 가 붙인 해시(FK6q4k2r... )라서 위 이름과 다르다.
--   이 상태로 앱을 띄우면 hbm2ddl.auto=update 가 "선언된 이름의 인덱스가 없다"고 보고
--   **같은 컬럼에 인덱스를 하나 더 만든다.** 쓰기 비용이 이중으로 든다.
--
--   이 스크립트는 그 해시 이름 인덱스를 **선언된 이름으로 개명**해서
--   hbm2ddl 이 아무것도 만들지 않게 한다.
--
-- =============================================================================
-- 왜 DROP 이 아니라 RENAME 인가 (MySQL 8.0 에서 실측한 근거)
--
--   ① DROP INDEX 는 FK 가 살아있는 동안 **거부된다.**
--        ALTER TABLE sigungu DROP INDEX FKhash...;
--        → ERROR 1553 (HY000): Cannot drop index 'FKhash...':
--                              needed in a foreign key constraint
--      즉 "FK 삭제 → 인덱스 삭제 → 앱 재기동으로 재생성" 순서를 강제당하고,
--      그 사이 인덱스가 없는 구간이 생기며 재생성 시 인덱스 재구축 비용도 든다.
--
--   ② RENAME INDEX 는 FK 가 살아있어도 **성공한다.** FK 는 개명된 인덱스를
--      그대로 계속 사용한다(실측 확인). 메타데이터만 바뀌므로
--      인덱스 재구축이 없고, 인덱스가 없는 구간도 생기지 않는다.
--
--   그래서 RENAME 을 택했다. 부수 효과로 **FK 삭제 스크립트와의 순서 의존성이 사라진다.**
--
-- 실행 순서
--   2026-08-11-drop-legacy-fk.sql 과 이 스크립트는 **순서가 상관없다.**
--   (①의 제약이 RENAME 에는 적용되지 않기 때문이다.)
--   다만 앱을 재기동하기 **전에** 이 스크립트를 돌려야 중복 인덱스가 생기지 않는다.
--   권장 순서: 이 스크립트 → drop-legacy-fk.sql → 앱 배포/재기동
--
-- 멱등성
--   이미 선언된 이름의 인덱스가 있으면 건너뛴다. 여러 번 실행해도 안전하다.
--
-- 빈 DB 에서는
--   실행할 필요가 없다. 개명할 옛 인덱스가 없으므로 아무것도 하지 않고,
--   hbm2ddl 이 @Index 선언대로 인덱스를 만든다.
--
-- 실행
--   docker compose exec -T mysql \
--     mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" smash_data \
--     < docker/mysql/ddl/2026-08-11-rename-fk-index.sql
-- =============================================================================


-- ── 1) 사전 확인 : 현재 대상 테이블의 인덱스 상태 ─────────────────────────────
SELECT
    s.TABLE_NAME,
    s.INDEX_NAME,
    s.SEQ_IN_INDEX,
    s.COLUMN_NAME,
    s.NON_UNIQUE
FROM information_schema.STATISTICS s
WHERE s.TABLE_SCHEMA = DATABASE()
  AND s.TABLE_NAME IN ('sigungu', 'job_code_middle', 'JobCount')
ORDER BY s.TABLE_NAME, s.INDEX_NAME, s.SEQ_IN_INDEX;


-- ── 2) 개명 ───────────────────────────────────────────────────────────────────
--
-- 옛 인덱스 이름이 해시라 하드코딩할 수 없으므로 조건으로 찾는다.
-- 후보 조건 (전부 만족해야 개명 대상):
--   * 대상 테이블의 인덱스
--   * PRIMARY 가 아니다
--   * NON_UNIQUE = 1  (유니크 제약이 만든 인덱스는 건드리지 않는다)
--   * 컬럼이 정확히 1개이고, 그 컬럼이 대상 컬럼이다
--   * 이름이 목표 이름과 다르다
--
-- ★ 마지막 두 조건이 JobCount 의 복합 유니크
--   (sigungu_code, job_code_middle_code) 를 자동으로 제외한다.
--   그 인덱스는 UNIQUE 이고 컬럼이 2개이므로 후보가 아니다.
--   이 복합 유니크는 JobCountBatch 의 ON DUPLICATE KEY UPDATE 가 의존하므로
--   절대 건드리면 안 된다.

DELIMITER $$

DROP PROCEDURE IF EXISTS rename_legacy_fk_index $$

CREATE PROCEDURE rename_legacy_fk_index()
BEGIN
    DECLARE v_done    INT DEFAULT 0;
    DECLARE v_table   VARCHAR(64);
    DECLARE v_column  VARCHAR(64);
    DECLARE v_desired VARCHAR(64);
    DECLARE v_old     VARCHAR(64);
    DECLARE v_exists  INT;

    DECLARE cur CURSOR FOR
        SELECT tbl, col, desired FROM tmp_index_targets;

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;

    rename_loop: LOOP
        FETCH cur INTO v_table, v_column, v_desired;
        IF v_done = 1 THEN
            LEAVE rename_loop;
        END IF;

        -- 이미 목표 이름의 인덱스가 있으면 건너뛴다 (멱등성)
        SELECT COUNT(*) INTO v_exists
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME   = v_table
          AND INDEX_NAME   = v_desired;

        IF v_exists > 0 THEN
            SELECT CONCAT('SKIP  : ', v_table, '.', v_desired, ' already exists') AS `결과`;
        ELSE
            -- 같은 컬럼 단독 · 비유니크 인덱스를 찾는다
            SET v_old = NULL;

            SELECT s.INDEX_NAME INTO v_old
            FROM information_schema.STATISTICS s
            WHERE s.TABLE_SCHEMA = DATABASE()
              AND s.TABLE_NAME   = v_table
              AND s.COLUMN_NAME  = v_column
              AND s.SEQ_IN_INDEX = 1
              AND s.NON_UNIQUE   = 1
              AND s.INDEX_NAME  <> 'PRIMARY'
              AND (SELECT COUNT(*)
                   FROM information_schema.STATISTICS s2
                   WHERE s2.TABLE_SCHEMA = s.TABLE_SCHEMA
                     AND s2.TABLE_NAME   = s.TABLE_NAME
                     AND s2.INDEX_NAME   = s.INDEX_NAME) = 1
            ORDER BY s.INDEX_NAME
            LIMIT 1;

            IF v_old IS NULL THEN
                SELECT CONCAT('NONE  : ', v_table, '.', v_column,
                              ' — 개명할 옛 인덱스 없음. hbm2ddl 이 ',
                              v_desired, ' 를 생성할 것이다') AS `결과`;
            ELSE
                SET @ddl = CONCAT('ALTER TABLE `', v_table,
                                  '` RENAME INDEX `', v_old,
                                  '` TO `', v_desired, '`');
                SELECT CONCAT('RENAME: ', @ddl) AS `결과`;
                PREPARE stmt FROM @ddl;
                EXECUTE stmt;
                DEALLOCATE PREPARE stmt;
            END IF;
        END IF;
    END LOOP;

    CLOSE cur;
END $$

DELIMITER ;

-- 대상 목록. 엔티티의 @Index 선언과 반드시 일치해야 한다.
DROP TEMPORARY TABLE IF EXISTS tmp_index_targets;
CREATE TEMPORARY TABLE tmp_index_targets (
    tbl     VARCHAR(64),
    col     VARCHAR(64),
    desired VARCHAR(64)
);
INSERT INTO tmp_index_targets (tbl, col, desired) VALUES
    ('sigungu',         'sido_code',            'idx_sigungu_sido'),
    ('job_code_middle', 'top_code',             'idx_job_code_middle_top'),
    ('JobCount',        'job_code_middle_code', 'idx_jobcount_job_code_middle');

CALL rename_legacy_fk_index();

DROP TEMPORARY TABLE IF EXISTS tmp_index_targets;
DROP PROCEDURE rename_legacy_fk_index;


-- ── 3) 사후 확인 : 선언된 3개 이름이 보이고, 같은 컬럼에 중복이 없어야 한다 ────
SELECT
    s.TABLE_NAME,
    s.INDEX_NAME,
    s.SEQ_IN_INDEX,
    s.COLUMN_NAME,
    s.NON_UNIQUE
FROM information_schema.STATISTICS s
WHERE s.TABLE_SCHEMA = DATABASE()
  AND s.TABLE_NAME IN ('sigungu', 'job_code_middle', 'JobCount')
ORDER BY s.TABLE_NAME, s.INDEX_NAME, s.SEQ_IN_INDEX;
