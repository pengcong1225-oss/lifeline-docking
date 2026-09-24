# lifeline-docking 部署手册（东京 VPS）

上线地址：<https://ai.mockr.com.cn/lifeline-docking/>
服务器：`195.245.241.88`（`s62478`），落盘目录 `/data/lifeline`

## 一、部署形态

| 项 | 值 |
|---|---|
| 运行方式 | Docker 容器 `lifeline-server`（镜像 `lifeline-docking:latest`） |
| 基础镜像 | `maven:3.9-eclipse-temurin-17` 构建 → `eclipse-temurin:17-jre-jammy` 运行 |
| 监听 | `127.0.0.1:3110`（**仅回环**，公网一律经 Nginx） |
| 数据库 | 复用既有 `gqxq-mysql` 容器，库 `lifeline_docking`，账号 `lifeline`（仅授权本库） |
| 网络 | 外部网络 `gqxq-net`（172.21.0.0/16） |
| 内存上限 | 768m |
| 日志 | json-file，10m × 3 |

端口选 3110 的原因：8080 已被 sub2api 占用，3100 已被 gqxq-server 占用。

## 二、公网暴露范围（重要）

应用层 `/admin/**` **无任何鉴权**（README 第六节明确写了生产前必须补）。
因此在 Nginx 层做了封闭，公网只放行调用方真正需要的两个入口：

| 路径 | 公网 | 说明 |
|---|---|---|
| `/lifeline-docking/data-docking-api/token` | ✅ | 调用方取令牌，限流 10r/s |
| `/lifeline-docking/data-docking-api/execute/api` | ✅ | 调用方推送数据，限流 10r/s |
| `/lifeline-docking/admin/` | ❌ 403 | 凭证登记、调试台、上收数据查询 |
| `/lifeline-docking/credentials.html` | ❌ 403 | 同上 |
| `/lifeline-docking/debug.html` | ❌ 403 | 调试台 |
| `/lifeline-docking/records.html` | ❌ 403 | 上收数据 |
| `/lifeline-docking/kb` | ❌ 403 | 知识库 |
| `/lifeline-docking/` 其余 | ❌ 403 | 兜底 |

生产配置 `LIFELINE_DEBUG_ENABLED=false`（调试台关闭）。

需要临时从公网访问管理页面时：在 VPS 上用 SSH 隧道走回环，不要放开 Nginx。

```
ssh -L 3110:127.0.0.1:3110 root@195.245.241.88
# 然后浏览器打开 http://127.0.0.1:3110/
```

## 三、凭据（服务器 `/data/lifeline/.env`，chmod 600）

| 键 | 用途 |
|---|---|
| `LIFELINE_DB_NAME` | `lifeline_docking` |
| `LIFELINE_DB_USER` / `LIFELINE_DB_PASSWORD` | 应用数据库账号（仅授权 `lifeline_docking`） |
| `LIFELINE_MASTER_KEY` | AES-256-GCM 主密钥（Base64，32 字节），加密企业 secretKey |

**主密钥一旦更换或丢失，已登记的 secretKey 全部无法解密** —— 与 gqxq 的 JWT secret 同等重要。
明文见服务器 `.env`，本仓库不存放。

## 四、数据库

三张表由应用启动时 `spring.sql.init` 自动执行 `schema.sql` 建立：

- `docking_caller_credential` —— 企业登记的老凭证（密钥密文）
- `docking_request_record` —— 请求留存信封
- `docking_data_item` —— 业务数据项，`UNIQUE KEY (tag, lsh)` 判重

建库建账号脚本：`/data/lifeline/init-db.sh`（幂等，可重复执行）。

```bash
docker exec -e MYSQL_ROOT_PASSWORD='<root口令>' -i gqxq-mysql bash -s < /data/lifeline/init-db.sh
```

## 五、常用运维

```bash
cd /data/lifeline

docker compose ps                  # 状态
docker compose logs -f --tail 100  # 日志
docker compose restart             # 重启
docker compose down                # 停止并移除容器

# 重新构建并上线（改代码后）
docker build --no-cache -t lifeline-docking:latest .
docker compose up -d

# 回滚到上一版本镜像（构建前先 docker tag 留档）
docker tag lifeline-docking:latest lifeline-docking:rollback-$(date +%Y%m%d-%H%M%S)
```

回滚 Nginx：删除 `ai.mockr.com.cn` 中 lifeline-docking 整段，
删 `/etc/nginx/conf.d/lifeline-ratelimit.conf`，`nginx -t && systemctl reload nginx`。
备份在 `/root/nginx-backup-lifeline-*`。

## 六、上线后待办（应用层未解决，非部署问题）

1. `/admin/**` 加鉴权 —— 目前靠 Nginx 403 顶着，一旦开放公网必须补。
2. 新平台自签令牌存内存，重启失效 —— 建议换 Redis 或数据库。
3. 签名规则、令牌参数位置与响应格式需与平台方联调确认（README 第五节）。
4. 老平台地址 `http://111.47.65.236:9910` 是**测试地址且明文 HTTP**，生产前须替换。
5. 业务自处理逻辑未实现（命中已接管类别返回 501）。

## 七、验证记录（2026-09-24）

- 容器 `lifeline-server` 状态 healthy，Tomcat 监听 3110，HikariPool 连库成功
- 三张表自动建立
- 回环冒烟：`/` 200、`/data-docking-api/token` 400、`/admin/api/stats` 200
- 公网实测（经域名 + 443）：
  - `token` 400、`execute/api` 401 —— 业务正常响应
  - `admin/api/stats`、`credentials.html`、`/` 全部 403
- 既有站点不受影响：`/`（NewAPI）200、`/gqxq/` 200

## 八、管理后台访问控制（2026-09-24 追加）

三个管理页面能增删改企业凭证、用真实凭证往老平台发数据、读取全部上收记录，
而应用层 /admin/** 无鉴权，故在 Nginx 层加 HTTP Basic 认证。

- 口令文件 `/etc/nginx/lifeline.htpasswd`（apr1，640；明文口令见本地留档）
- 免认证：首页、知识库 /kb/index.html、两个调用接口
- 需认证：credentials.html、debug.html、records.html、/admin/**
- 复用片段：`/etc/nginx/snippets/lifeline-proxy.conf`
- Nginx 备份：`/root/nginx-backup-lifeline-preauth-20260924-1515`

改口令：`printf '%s' "新口令" | openssl passwd -apr1 -stdin`，替换 htpasswd 内容后 reload。