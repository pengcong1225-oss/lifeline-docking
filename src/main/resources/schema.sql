-- 城市生命线数据上收开放平台 —— 表结构
-- 由 spring.sql.init 在启动时执行，全部使用 IF NOT EXISTS，可重复运行。

-- 1. 调用者登记的老平台凭证（secretKey 只存密文）
CREATE TABLE IF NOT EXISTS docking_caller_credential (
  id             BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  access_key     VARCHAR(128)  NOT NULL COMMENT '老平台分配的 accessKey',
  secret_cipher  VARCHAR(2048) NOT NULL COMMENT 'secretKey 密文：AES-256-GCM，Base64(iv||cipher)',
  secret_hint    VARCHAR(32)   NOT NULL DEFAULT '' COMMENT '密钥尾号，仅用于人工识别',
  company_name   VARCHAR(128)  NOT NULL DEFAULT '' COMMENT '企业名称',
  contact        VARCHAR(64)   NOT NULL DEFAULT '' COMMENT '联系人',
  enabled        TINYINT       NOT NULL DEFAULT 1 COMMENT '1=启用 0=停用',
  verified_at    DATETIME      NULL COMMENT '最近一次向老平台取令牌验证成功的时间',
  verify_message VARCHAR(512)  NOT NULL DEFAULT '' COMMENT '最近一次验证结果',
  created_at     DATETIME      NOT NULL COMMENT '创建时间',
  updated_at     DATETIME      NOT NULL COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_access_key (access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调用者登记的老平台凭证';

-- 2. 业务请求留存（信封）。新平台保存失败时不得继续转发。
CREATE TABLE IF NOT EXISTS docking_request_record (
  record_id         VARCHAR(64)   NOT NULL COMMENT '留存ID',
  access_key        VARCHAR(128)  NOT NULL COMMENT '调用者',
  api_cmd           VARCHAR(128)  NOT NULL COMMENT '路由：apiCmd',
  tag               VARCHAR(128)  NOT NULL COMMENT '路由：apiBody.tag',
  operation_type    VARCHAR(16)   NOT NULL DEFAULT '' COMMENT '路由：apiBody.operationType',
  data_count        INT           NOT NULL DEFAULT 0 COMMENT '请求中的数据条数',
  stored_count      INT           NOT NULL DEFAULT 0 COMMENT '成功入库条数',
  duplicate_count   INT           NOT NULL DEFAULT 0 COMMENT '按 lsh 判重的重复条数',
  raw_body          MEDIUMTEXT    NOT NULL COMMENT '原始请求体',
  status            VARCHAR(32)   NOT NULL COMMENT 'RECEIVED/ACCEPTED_OLD/REJECTED_OLD/UNKNOWN',
  message           VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '处理说明',
  old_platform_code INT           NULL COMMENT '老平台业务码，NULL 表示未取得',
  old_response      MEDIUMTEXT    NULL COMMENT '老平台原始响应',
  source            VARCHAR(16)   NOT NULL DEFAULT 'API' COMMENT 'API=调用者推送，DEBUG=调试台',
  received_at       DATETIME(3)   NOT NULL COMMENT '接收时间',
  forwarded_at      DATETIME(3)   NULL COMMENT '转发完成时间',
  PRIMARY KEY (record_id),
  KEY idx_received_at (received_at),
  KEY idx_route (api_cmd, tag, operation_type),
  KEY idx_status (status),
  KEY idx_access_key (access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='业务请求留存';

-- 3. 上收的业务数据项
CREATE TABLE IF NOT EXISTS docking_data_item (
  id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  record_id      VARCHAR(64)  NOT NULL COMMENT '所属留存记录',
  access_key     VARCHAR(128) NOT NULL COMMENT '调用者',
  api_cmd        VARCHAR(128) NOT NULL,
  tag            VARCHAR(128) NOT NULL,
  operation_type VARCHAR(16)  NOT NULL DEFAULT '',
  item_index     INT          NOT NULL DEFAULT 0 COMMENT '在 data[] 中的下标',
  lsh            VARCHAR(128) NULL COMMENT '流水号；老平台按此做唯一校验',
  payload        JSON         NOT NULL COMMENT '原始数据项',
  created_at     DATETIME(3)  NOT NULL,
  PRIMARY KEY (id),
  -- tag 内 lsh 唯一；lsh 为 NULL 的接口（如第三方巡检）不受此约束
  UNIQUE KEY uk_tag_lsh (tag, lsh),
  KEY idx_record (record_id),
  KEY idx_created (created_at),
  KEY idx_tag (tag)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='上收业务数据项';
