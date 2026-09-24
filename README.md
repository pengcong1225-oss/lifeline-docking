# lifeline-docking

城市生命线数据上收开放平台（新平台）。Java 17 + Spring Boot 3.2.5 + MyBatis-Plus 3.5.5 + MySQL 8 + Maven。

依据交接文档《新平台接入与替换老平台_交接文档.md》实现，目标是：**调用者只替换接口地址**，
继续使用老平台分配的 `accessKey/secretKey`、原请求路径、请求字段和调用方式。

## 知识库

接口对接知识与项目实现知识沉淀在 [`docs/kb/`](docs/kb/)，**可在浏览器共享查看**：

> **启动应用后访问 <http://localhost:8080/kb/>** —— 左侧目录 + 全文搜索，无外部依赖，可直接把地址发给协作方。

覆盖：接入总览、19 个接口清单与逐字段明细、路由规则与陷阱、公共字段约定、数据字典（28 类摘录 + 93 类索引）、
平台架构、数据库设计、部署与运行、联调阻塞项、验证记录、FAQ。

源文件是 Markdown（`docs/kb/*.md`），可 diff、可评审；其中两页由工具自动生成：

```powershell
node tools/build-kb.mjs           # 刷新 02-接口清单.md 并重新生成 kb/index.html
node tools/extract-dictionary.mjs # 刷新 05-数据字典.md（依赖源文档）
```

## 一、整体数据流

```text
企业登记：提交老平台 accessKey/secretKey
            → AES-256-GCM 加密后入库（明文不回显、不落日志）

调用者取令牌：POST /data-docking-api/token
            → 新平台按老平台兼容规则验签 → 签发【新平台自己的】令牌
            → 老平台令牌绝不返回给调用者

调用者推送：POST /data-docking-api/execute/api  (Authorization: Bearer <新平台令牌>)
            → 验令牌、识别调用者
            → 可靠留存原始请求（留存失败则绝不转发）
            → 数据项按 lsh 判重入库
            → 按 apiCmd + tag + operationType 路由
                ├─ 已接管：新平台自行处理（第一版未实现，命中返回 501）
                └─ 未接管：用该调用者老凭证在内部取老令牌 → 同步转发老平台
                          只有老平台业务 code=0 才算成功
```

**成功判定**：只看老平台业务 `code=0`。HTTP 200 不代表成功，`data=true` 也不代表成功。

## 二、四项能力的入口

### 1. 企业填写自己的老凭证

- 页面：<http://localhost:8080/credentials.html>
- 接口：`POST/GET /admin/api/credentials`、`PUT /admin/api/credentials/{id}`、
  `POST /admin/api/credentials/{id}/enabled`、`DELETE /admin/api/credentials/{id}`
- `POST /admin/api/credentials/{id}/verify` 会用该凭证向老平台真实取一次令牌做验证（联调动作）

`secretKey` 用 **AES-256-GCM** 加密存储，格式 `Base64(iv(12) || ciphertext+tag)`；
任何响应、日志都只出现尾部提示（如 `****0001`）。

### 2. 全部老平台接口的上传调试能力

- 页面：<http://localhost:8080/debug.html>
- 接口：`GET /admin/api/interfaces`、`GET /admin/api/interfaces/{seq}`、
  `GET /admin/api/interfaces/{seq}/sample?count=N`、`POST /admin/api/debug/send`

覆盖源文档全部 **19 个条目**（1 个令牌接口 + 18 个业务操作）。调试台可以：

1. 选择已登记凭证；
2. 选择 18 个业务接口之一，按字段 Schema 自动生成报文模板（含示例值）；
3. 在线编辑 JSON 后发送。

发送走的是与调用者推送**完全相同**的链路（留存 → 入库 → 转发），**没有跳过留存的旁路**。

18 个业务接口清单（路由键 = `apiCmd / tag / operationType`）：

| # | 业务类别 | 路由键 | 字段数 |
| ---: | --- | --- | ---: |
| 2 | 第三方施工信息 | `lifeline_data_batch_acces / construction_info` | 21 |
| 3 | 第三方施工进度 | `lifeline_data_batch_acces / construction_progress` | 13 |
| 4 | 用户信息（保存或更新） | `inspection_third_party_data_access / risk / I` | 12 |
| 5 | 隐患信息 | `lifeline_data_batch_acces / ycrqqy_hazard` | 35 |
| 6 | 隐患处置信息 | `lifeline_data_batch_acces / ycrqqy_hazard_disposal` | 13 |
| 7 | 监测点位信息 | `lifeline_data_batch_acces / ycrqqy_location` | 15 |
| 8 | 监测设备信息 | `lifeline_data_batch_acces / ycrqqy_device` | 29 |
| 9 | 实时监测数据 | `lifeline_data_batch_acces / ycrqqy_realtime` | 11 |
| 10 | 设备报警信息 | `lifeline_data_batch_acces / ycrqqy_alarm` | 26 |
| 11 | 报警处置信息 | `lifeline_data_batch_acces / ycrqqy_alarm_disposal` | 11 |
| 12 | 巡检人员（删除） | `inspection_third_party_data_access / inspector / D` | 2 |
| 13 | 巡检人员（保存或更新） | `inspection_third_party_data_access / inspector / I` | 8 |
| 14 | 巡检片区（保存或更新） | `inspection_third_party_data_access / district / I` | 4 |
| 15 | 巡检片区（删除） | `inspection_third_party_data_access / district / D` | 1 |
| 16 | 巡检轨迹（保存或更新） | `inspection_third_party_data_access / route / I` | 7 |
| 17 | 供气量信息 | `lifeline_data_batch_acces / gspsp_gasflowhour` | 10 |
| 18 | 第三方巡检隐患（保存或更新） | `inspection_third_party_data_access / risk / I` | 12 |
| 19 | 巡检隐患处置（保存或更新） | `inspection_third_party_data_access / riskDispose / I` | 7 |

> `lifeline_data_batch_acces` 少一个 s 是**源文档原文拼写，不能改**（有单测锁定）。
>
> 条目 4 与 18 的路由键**完全相同**，源文档未说明区别 —— 见下文阻塞项。

### 3. 上收数据入库存储

表结构见 `src/main/resources/schema.sql`，启动时自动建表：

| 表 | 用途 |
| --- | --- |
| `docking_caller_credential` | 企业登记的老凭证（密钥密文） |
| `docking_request_record` | 请求留存信封：原始报文、路由键、状态、老平台响应 |
| `docking_data_item` | 逐条业务数据项；`UNIQUE KEY (tag, lsh)` 做流水号唯一校验 |

- 页面：<http://localhost:8080/records.html>
- 接口：`GET /admin/api/records`、`GET /admin/api/records/{recordId}`、`GET /admin/api/stats`

请求状态机：`RECEIVED`（已留存未转发）、`ACCEPTED_OLD`、`REJECTED_OLD`、
`REJECTED_LOCAL`（新平台自身拒绝）、`UNKNOWN`（超时/断线，结果未知）。

### 4. 新平台用老凭证把数据发到老平台

`OldPlatformTokenService` 用该调用者的老凭证在内部取老令牌并缓存，
`OldPlatformClient` 用老令牌转发原始报文。老令牌只在新平台内部使用。

关键安全行为：

- 留存失败 → **不转发**；
- 老平台超时/断线 → 状态 `UNKNOWN`，对调用者返回 **HTTP 502**，绝不伪装成功，也**不自动重放**
  （老平台幂等性尚未验证）；
- 老平台返回非零业务码 → 清掉令牌缓存，下次请求重新取令牌。

## 三、本地运行

```powershell
# 1) 配置主密钥（生产必须固定注入，否则重启后已登记密钥无法解密）
$env:LIFELINE_MASTER_KEY = (openssl rand -base64 32)

# 2) 数据库（默认连 localhost:3306，createDatabaseIfNotExist 会自动建库）
#    口令没有默认值，必须由环境变量注入，禁止写进仓库。
$env:LIFELINE_DB_USER = 'root'
$env:LIFELINE_DB_PASSWORD = '***'
#    或：复制 src/main/resources/application-local.yml.example 为 application-local.yml
#        （已在 .gitignore 中）后加 --spring.profiles.active=local 启动。

# 3) 启动
C:\maven\bin\mvn.cmd -f pom.xml spring-boot:run
```

默认端口 8080，首页 <http://localhost:8080/>。

### 配置项

| 配置 | 默认 | 说明 |
| --- | --- | --- |
| `lifeline.old-platform.base-url` | `http://111.47.65.236:9910` | 老平台地址（**测试地址，非生产**） |
| `lifeline.old-platform.token-param-style` | `query` | 取令牌参数位置：`query` 或 `form` |
| `lifeline.signature.param-order` | `sorted` | 签名参数顺序：`sorted`（key 升序）或 `given`（文档顺序） |
| `lifeline.signature.timestamp-tolerance-seconds` | `300` | 时间戳允许偏差 |
| `lifeline.crypto.master-key` | 空 | AES 主密钥（Base64，32 字节） |
| `lifeline.debug.enabled` | `true` | 调试台开关，**生产应关闭** |
| `lifeline.token.ttl-seconds` | `7200` | 新平台自签令牌有效期 |
| `lifeline.token.old-token-cache-seconds` | `1800` | 老令牌本地缓存时长 |

## 四、本机联调验证（不碰真实老平台）

`tools/` 下提供了模拟器与冒烟测试，**可以在完全不接触真实老平台的前提下验证全链路**：

```powershell
# 1) 启动老平台模拟器（端口 9999）
node tools/mock-old-platform.mjs 9999

# 2) 让新平台指向模拟器后启动
$env:LIFELINE_OLD_BASE_URL = 'http://localhost:9999'

# 3) 跑端到端冒烟测试（40 项断言）
node tools/smoke-test.mjs
```

冒烟测试带**安全闸**：如果目标老平台不是 mock 模拟器（没有 `/_mock/received` 端点），
脚本会直接中止，避免把测试数据推到真实老平台。

覆盖场景：凭证登记与 UTF-8 往返、密钥不回显、向老平台验证凭证、接口清单完整性、
留存+入库+转发全链路、重复流水号判重、老平台业务失败（`REJECTED_OLD`）、
老平台超时（`UNKNOWN` + 调用者侧 502）、非法令牌 401、错误签名 401。

### 其他工具

```powershell
# 源文档更新后重新生成接口清单（默认读交接资料目录，可用 LIFELINE_FIELD_DOC 覆盖）
node tools/build-catalog.mjs [源文档路径]

# 单元测试（31 项）
C:\maven\bin\mvn.cmd -o -f pom.xml test
```

## 五、尚未完成 / 联调阻塞项

**未实现（当前为占位或空缺）**

- 任一业务类别的自处理逻辑：`RouteRegistry` 接管集合为空，命中已接管类别返回 501；
- 新平台自签令牌存在内存，重启即失效（应换成 Redis 或数据库）；
- 没有后台账号体系与权限控制，`/admin/**` 当前无鉴权 —— 生产前必须补上；
- 没有对调用者暴露的限流、审计与对账；
- 历史数据迁移、部署、生产地址。

**必须与平台方联调确认的阻塞项**（源文档未定义）

1. **签名细节**：参数顺序、字符编码、签名输出格式、时间戳容差。
   `lifeline.signature.param-order` 提供 `sorted`/`given` 两种策略供比对，但必须用真实
   请求样本或官方测试向量确定。
2. **`/token` 参数位置**：接口页写 query，接入说明写 `multipart/form-data`。
   控制器两者都收，转发侧用 `token-param-style` 切换，但需观察真实调用者行为。
3. **`/token` 响应格式**：接口页 Schema 是 `{}`，接入说明有 `data.token/issuedAt/expiresAt`，
   且示例令牌自带 `Bearer ` 前缀。当前按接入说明返回、并会剥离老平台令牌的 `Bearer ` 前缀，
   但需真实成功/失败样本核对。
4. **测试凭证**：仓库内不含任何可用凭证，`tools/mock-old-platform.mjs` 里的
   `mock-ak-0001/mock-sk-0001` 仅为本机模拟器使用。
5. **业务语义**：批量大小上限、部分成功、删除/更新语义、`lsh` 冲突后老平台的行为。
   第一版原样转发，由老平台判定。
6. **条目 4 与 18 路由键重复**：两者均为 `inspection_third_party_data_access / risk / I`，
   且请求模型重合，源文档未说明区别（已固化为单测提醒）。

## 六、安全约束

- `secretKey` 明文**不得**写入日志、工单、Git 或交接文档；代码中只在签名瞬间于内存解密。
- 主密钥通过 `LIFELINE_MASTER_KEY` 注入并妥善保管；**更换或丢失会导致已登记密钥无法解密**。
- 不要向老平台业务接口发送测试数据，除非已获得测试环境与明确授权。
  默认用 `tools/mock-old-platform.mjs` 验证。
- 生产环境必须关闭 `lifeline.debug.enabled`，并给 `/admin/**` 加上鉴权。
- 老平台测试地址是明文 HTTP；生产不应未经网络安全评估直接沿用。
