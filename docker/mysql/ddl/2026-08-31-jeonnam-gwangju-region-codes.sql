-- 전남광주통합특별시 출범(2026-07-01)에 따른 시군구 코드 이관
--
-- 광주광역시(29xxx) + 전라남도(46xxx) 27개 시군구가 새 시도 코드 12 아래로 재부여됐다.
-- 옛 코드는 외부 API 에서 전 기간 0건이라 해당 지역이 전면 결측됐다.
-- 코드표 출처: 행정표준코드관리시스템(code.go.kr) 정본, 2026-08-31 다운로드.
--
-- ────────────────────────────────────────────────────────────────────────────
-- 실행 시점: **배포 후, 시드 배치(seedMasterJob)가 끝난 뒤**
-- ────────────────────────────────────────────────────────────────────────────
-- 배포 전에 돌리면 구버전 앱이 옛 코드로 조회하다 데이터를 잃는다.
-- 시드가 sigungu 에 12xxx 27행을 먼저 넣어야 마스터가 비는 구간이 없다.
--
-- 실행:
--   docker exec -i smash-mysql-1 sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" smash' \
--     < docker/mysql/ddl/2026-08-31-jeonnam-gwangju-region-codes.sql
--
-- 재실행 안전(idempotent). 옛 코드가 없으면 0행 영향으로 끝난다.

-- ── 사전 점검 ───────────────────────────────────────────────────────────────
-- 실행 전에 이 쿼리로 옛 코드가 몇 행 남았는지 본다.
--
--   SELECT 'population' t, COUNT(*) c FROM population           WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'dwelling',           COUNT(*) FROM dwelling            WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'dwelling_by_type',   COUNT(*) FROM dwelling_by_type    WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'infra',              COUNT(*) FROM infra               WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'infra_staging_count',COUNT(*) FROM infra_staging_count WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'JobCount',           COUNT(*) FROM JobCount            WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'region_job_stat',    COUNT(*) FROM region_job_statistics WHERE LEFT(sigungu_code,2) IN ('29','46')
--   UNION ALL SELECT 'sigungu',            COUNT(*) FROM sigungu             WHERE LEFT(sigungu_code,2) IN ('29','46');

START TRANSACTION;

-- ── 1. 코드 대응표 ──────────────────────────────────────────────────────────
-- 옛 코드 오름차순과 신규 순서가 어긋나는 구간이 있다(고흥군 46770 → 12740).
-- 순서로 추정하지 말고 이 표를 쓴다.
CREATE TEMPORARY TABLE tmp_region_code_map (
    old_code CHAR(5) NOT NULL PRIMARY KEY,
    new_code CHAR(5) NOT NULL,
    name     VARCHAR(40) NOT NULL
);

INSERT INTO tmp_region_code_map (old_code, new_code, name) VALUES
  ('46110','12110','목포시'), ('46130','12130','여수시'), ('46150','12150','순천시'),
  ('46170','12170','나주시'), ('46230','12190','광양시'),
  ('29110','12210','동구'),   ('29140','12240','서구'),   ('29155','12270','남구'),
  ('29170','12300','북구'),   ('29200','12330','광산구'),
  ('46710','12710','담양군'), ('46720','12720','곡성군'), ('46730','12730','구례군'),
  ('46770','12740','고흥군'), ('46780','12750','보성군'), ('46790','12760','화순군'),
  ('46800','12770','장흥군'), ('46810','12780','강진군'), ('46820','12790','해남군'),
  ('46830','12800','영암군'), ('46840','12810','무안군'), ('46860','12820','함평군'),
  ('46870','12830','영광군'), ('46880','12840','장성군'), ('46890','12850','완도군'),
  ('46900','12860','진도군'), ('46910','12870','신안군');

-- ── 2. 하위 테이블: 옛 코드 → 신규 코드로 이관 ─────────────────────────────
-- 이미 쌓인 데이터를 버리지 않는다. 새 코드 행이 아직 없으므로 유니크 충돌이 없다.
-- (region_job_statistics 는 예외 — 3번에서 따로 다룬다)

UPDATE population p
  JOIN tmp_region_code_map m ON m.old_code = p.sigungu_code
   SET p.sigungu_code = m.new_code;

UPDATE dwelling d
  JOIN tmp_region_code_map m ON m.old_code = d.sigungu_code
   SET d.sigungu_code = m.new_code;

UPDATE dwelling_by_type d
  JOIN tmp_region_code_map m ON m.old_code = d.sigungu_code
   SET d.sigungu_code = m.new_code;

UPDATE infra i
  JOIN tmp_region_code_map m ON m.old_code = i.sigungu_code
   SET i.sigungu_code = m.new_code;

UPDATE infra_staging_count s
  JOIN tmp_region_code_map m ON m.old_code = s.sigungu_code
   SET s.sigungu_code = m.new_code;

UPDATE JobCount j
  JOIN tmp_region_code_map m ON m.old_code = j.sigungu_code
   SET j.sigungu_code = m.new_code;

-- ── 3. region_job_statistics: 옛 행 삭제 ───────────────────────────────────
-- 이 테이블은 시드 CSV(eis_job_stats.csv)가 12xxx 로 전량 재적재한다.
-- UPDATE 하면 uk_region_job_stat(sigungu_code, job_top_code, stat_month) 이 충돌한다.
DELETE r FROM region_job_statistics r
  JOIN tmp_region_code_map m ON m.old_code = r.sigungu_code;

-- ── 4. 마스터 정리 ────────────────────────────────────────────────────────
-- 시드는 upsert 라 옛 행을 지우지 않는다. 남겨두면 지역 목록에 27개가 중복된다.
DELETE s FROM sigungu s
  JOIN tmp_region_code_map m ON m.old_code = s.sigungu_code;

DELETE FROM sido WHERE sido_code IN ('29', '46');

DROP TEMPORARY TABLE tmp_region_code_map;

COMMIT;

-- ── 사후 확인 ──────────────────────────────────────────────────────────────
--   SELECT COUNT(*) FROM sigungu;                                  -- 272 여야 한다
--   SELECT COUNT(*) FROM sido;                                     -- 16 이어야 한다
--   SELECT COUNT(*) FROM sigungu WHERE LEFT(sigungu_code,2)='12';  -- 27
--   SELECT COUNT(*) FROM region_job_statistics
--     WHERE LEFT(sigungu_code,2)='12';                             -- 12636
--
-- ── 남은 일 ────────────────────────────────────────────────────────────────
-- Redis 캐시에는 옛 코드 키가 남는다. 점수·공고 캐시는 TTL 이 있어 자연 만료되지만,
-- support 정본(TTL 없음)은 수동 정리가 필요하다.
--   docker exec smash-my-redis-1 sh -c 'redis-cli --scan --pattern "*:29*" | xargs -r redis-cli DEL'
--   docker exec smash-my-redis-1 sh -c 'redis-cli --scan --pattern "*:46*" | xargs -r redis-cli DEL'
-- 패턴은 실제 키 네이밍을 확인하고 쓸 것. 넓게 잡으면 다른 지역까지 지운다.
