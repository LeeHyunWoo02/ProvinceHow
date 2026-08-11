-- =============================================================================
-- DDD 헥사고날 전환 마무리 — 옛 물리 FK 제약 삭제
--
-- 배경
--   architecture-conventions §5.2 / persistence-conventions §2.2 에 따라 모든 JPA
--   엔티티에서 Aggregate 간 객체 참조(@ManyToOne / @OneToOne / @MapsId)를 제거하고
--   sigungu_code 같은 "코드 값 컬럼"으로 바꿨다. 컬럼명은 그대로라 스키마 변경이
--   없었고, 그래서 **DB에는 옛 물리 FK 제약이 그대로 남아 있다**.
--   (hbm2ddl.auto=update 는 제약을 절대 삭제하지 않는다.)
--
--   architecture-conventions §9 와 persistence-conventions §8 이 정한 대로,
--   실제 FK 제약 삭제는 전환이 끝난 뒤 **별도 DDL** 로 수행한다. 이 파일이 그것이다.
--
-- 왜 지우는가
--   참조 무결성은 이제 물리 FK 가 아니라 (1) 배치 실행 순서(@Order 1~9)와
--   (2) Processor 의 값 객체 생성 검증이 보장한다 → persistence-conventions §7.3
--   FK 를 남겨두면 배치 Upsert 순서에 불필요한 결합이 남고, 컨텍스트 경계를
--   넘는 물리적 결합이 DB 에 계속 존재하게 된다.
--
-- =============================================================================
-- ⚠️ 실행 전 반드시 읽을 것
--
--   1. 이 스크립트는 **자동 실행되지 않는다.** docker/mysql/init/ 이 아니라
--      docker/mysql/ddl/ 에 둔 이유다. init/ 은 볼륨이 빈 최초 1회만 도는데,
--      그 시점엔 업무 테이블이 아직 없어서 여기 있는 ALTER 가 전부 실패한다.
--
--   2. **운영 DB 에 돌리기 전에 백업**하라. FK 삭제 자체는 데이터를 지우지 않지만
--      되돌리려면 제약을 손으로 다시 만들어야 하고, 그 사이 유입된 고아 행은
--      제약 재생성을 실패시킨다.
--
--   3. **인덱스는 건드리지 않는다.** InnoDB 는 FK 를 만들 때 인덱스를 자동 생성하고,
--      FK 를 DROP 해도 그 인덱스는 남는다. 조회 성능이 여기에 의존하므로 그대로 둔다.
--      → 아래 "남은 과제" 참고
--
--   4. 데이터 스키마(기본 smash_data)에 접속해서 실행하라. 스크립트는 DATABASE() 로
--      현재 접속 스키마를 대상으로 삼는다. smash_meta(배치 메타)에서 돌리면 안 된다.
--
-- 실행
--   docker compose exec -T mysql \
--     mysql -u"$MYSQL_USER" -p"$MYSQL_PASSWORD" smash_data \
--     < docker/mysql/ddl/2026-08-11-drop-legacy-fk.sql
--
-- 멱등성
--   이미 지워진 제약은 조회되지 않으므로 여러 번 실행해도 안전하다.
-- =============================================================================


-- ── 1) 사전 확인 (dry-run) : 무엇이 지워질지 먼저 눈으로 본다 ──────────────────
SELECT
    tc.TABLE_NAME,
    tc.CONSTRAINT_NAME,
    kcu.COLUMN_NAME,
    kcu.REFERENCED_TABLE_NAME,
    kcu.REFERENCED_COLUMN_NAME
FROM information_schema.TABLE_CONSTRAINTS tc
JOIN information_schema.KEY_COLUMN_USAGE kcu
      ON  kcu.CONSTRAINT_SCHEMA = tc.CONSTRAINT_SCHEMA
      AND kcu.CONSTRAINT_NAME   = tc.CONSTRAINT_NAME
      AND kcu.TABLE_NAME        = tc.TABLE_NAME
WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
  AND tc.CONSTRAINT_TYPE   = 'FOREIGN KEY'
  AND tc.TABLE_NAME IN ('sido', 'sigungu', 'population', 'dwelling',
                        'industry', 'infra',
                        'job_code_top', 'job_code_middle', 'JobCount')
ORDER BY tc.TABLE_NAME, tc.CONSTRAINT_NAME;


-- ── 2) 삭제 ───────────────────────────────────────────────────────────────────
--
-- 제약 이름을 하드코딩하지 않는 이유:
--   As-Is 엔티티가 @ForeignKey(name=...) 를 지정하지 않아 Hibernate 가
--   FK6q4k2r... 같은 **해시 기반 이름**을 붙였다. 이 이름은 스키마를 다시 만들면
--   달라지므로, 이름으로 DROP 하는 스크립트는 환경마다 깨진다.
--   그래서 information_schema 로 찾아서 지운다.
--
-- 대상 테이블을 IN 목록으로 못박은 이유:
--   이 프로젝트가 만든 업무 테이블에만 손대기 위해서다. 같은 스키마에 다른
--   테이블이 있어도 건드리지 않는다.

DELIMITER $$

DROP PROCEDURE IF EXISTS drop_legacy_aggregate_fk $$

CREATE PROCEDURE drop_legacy_aggregate_fk()
BEGIN
    DECLARE v_done       INT DEFAULT 0;
    DECLARE v_table      VARCHAR(64);
    DECLARE v_constraint VARCHAR(64);

    DECLARE cur CURSOR FOR
        SELECT TABLE_NAME, CONSTRAINT_NAME
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND CONSTRAINT_TYPE   = 'FOREIGN KEY'
          AND TABLE_NAME IN ('sido', 'sigungu', 'population', 'dwelling',
                             'industry', 'infra',
                             'job_code_top', 'job_code_middle', 'JobCount');

    DECLARE CONTINUE HANDLER FOR NOT FOUND SET v_done = 1;

    OPEN cur;

    drop_loop: LOOP
        FETCH cur INTO v_table, v_constraint;
        IF v_done = 1 THEN
            LEAVE drop_loop;
        END IF;

        SET @ddl = CONCAT('ALTER TABLE `', v_table, '` DROP FOREIGN KEY `', v_constraint, '`');
        SELECT @ddl AS `실행중`;

        PREPARE stmt FROM @ddl;
        EXECUTE stmt;
        DEALLOCATE PREPARE stmt;
    END LOOP;

    CLOSE cur;
END $$

DELIMITER ;

CALL drop_legacy_aggregate_fk();

DROP PROCEDURE drop_legacy_aggregate_fk;


-- ── 3) 사후 확인 : 0 행이어야 한다 ────────────────────────────────────────────
SELECT
    tc.TABLE_NAME,
    tc.CONSTRAINT_NAME
FROM information_schema.TABLE_CONSTRAINTS tc
WHERE tc.CONSTRAINT_SCHEMA = DATABASE()
  AND tc.CONSTRAINT_TYPE   = 'FOREIGN KEY'
  AND tc.TABLE_NAME IN ('sido', 'sigungu', 'population', 'dwelling',
                        'industry', 'infra',
                        'job_code_top', 'job_code_middle', 'JobCount');


-- ── 4) 참고 : 남는 인덱스 확인 ────────────────────────────────────────────────
-- FK 를 지워도 InnoDB 가 FK 용으로 만든 인덱스는 남는다. 아래로 확인할 수 있다.
--
--   SHOW INDEX FROM `sigungu`;
--   SHOW INDEX FROM `population`;
--   SHOW INDEX FROM `dwelling`;
--   SHOW INDEX FROM `JobCount`;
--   SHOW INDEX FROM `job_code_middle`;
--
-- ⚠️ 남은 과제 (이 스크립트의 범위 밖)
--   infra 만 @Index(idx_infra_sigungu / idx_infra_industry) 를 엔티티에 명시했다.
--   sigungu(sido_code) · population(sigungu_code) · dwelling(sigungu_code) ·
--   JobCount(sigungu_code, job_code_middle_code) · job_code_middle(top_code) 는
--   인덱스를 엔티티에 선언하지 않아, 지금은 **옛 FK 가 남긴 인덱스에 의존**하고 있다.
--   빈 DB 에서 hbm2ddl 로 스키마를 새로 만들면 이 인덱스들이 생기지 않아 조회가 느려진다.
--   → persistence-conventions §2.5 가 경고하는 상황. 해당 JpaEntity 에
--     @Index 를 명시적으로 추가하는 작업이 별도로 필요하다.
