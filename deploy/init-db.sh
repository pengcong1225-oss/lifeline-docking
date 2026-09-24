#!/usr/bin/env bash
# 在既有 gqxq-mysql 容器里为 lifeline-docking 建库与专用账号。
# 只授权 lifeline_docking 库，不动 gqxq_service。
#
# 口令不写死在脚本里，由环境变量提供：
#   LIFELINE_MYSQL_ROOT_PASSWORD  —— gqxq-mysql 的 root 口令（见本地留档）
#   LIFELINE_DB_PASSWORD          —— 应用账号 lifeline 的口令
#
# 用法（在 VPS 上）：
#   docker exec -e MYSQL_ROOT_PASSWORD="$ROOT_PW" -e LIFELINE_DB_PASSWORD="$APP_PW" \
#     -i gqxq-mysql bash -s < deploy/init-db.sh
set -euo pipefail

DB_NAME="${LIFELINE_DB_NAME:-lifeline_docking}"
DB_USER="${LIFELINE_DB_USER:-lifeline}"

: "${MYSQL_ROOT_PASSWORD:?需要设置 MYSQL_ROOT_PASSWORD}"
: "${LIFELINE_DB_PASSWORD:?需要设置 LIFELINE_DB_PASSWORD}"

mysql -uroot -p"$MYSQL_ROOT_PASSWORD" <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME}
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'%' IDENTIFIED BY '${LIFELINE_DB_PASSWORD}';
ALTER USER '${DB_USER}'@'%' IDENTIFIED BY '${LIFELINE_DB_PASSWORD}';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, DROP, ALTER, INDEX, REFERENCES
  ON ${DB_NAME}.* TO '${DB_USER}'@'%';
FLUSH PRIVILEGES;
SQL

echo "--- databases ---"
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW DATABASES;"
echo "--- grants for ${DB_USER} ---"
mysql -uroot -p"$MYSQL_ROOT_PASSWORD" -e "SHOW GRANTS FOR '${DB_USER}'@'%';"
