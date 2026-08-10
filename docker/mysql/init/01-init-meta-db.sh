#!/bin/bash
#
# MySQL 컨테이너 최초 기동 시 1회 실행된다 (volume이 비어 있을 때만).
#
# 이 프로젝트는 DataSource를 2개 쓴다.
#   - data 스키마: 업무 데이터. MYSQL_DATABASE 로 이미 생성됨 (DataDBConfig)
#   - meta 스키마: Spring Batch 메타 테이블. 여기서 만든다 (MetaDBConfig)
#
# 배치 메타 테이블(BATCH_JOB_INSTANCE 등)은 애플리케이션이 만든다.
# backend.env 의 BATCH_SCHEMA_INIT=always 여야 최초 기동 시 생성된다.
#
set -e

META_DB="${MYSQL_META_DB:-smash_meta}"

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<-EOSQL
    CREATE DATABASE IF NOT EXISTS \`${META_DB}\`
        CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

    GRANT ALL PRIVILEGES ON \`${META_DB}\`.* TO '${MYSQL_USER}'@'%';
    FLUSH PRIVILEGES;
EOSQL

echo "[init] meta 스키마 준비 완료: ${META_DB} (user=${MYSQL_USER})"
