-- ============================================================
-- admin-server schema (local profile 用 H2 内存库自动初始化；
-- redis-mysql profile 仅作参考脚本，部署时手动执行)
-- ============================================================

-- 管理员账号表
CREATE TABLE IF NOT EXISTS admin_user (
  id                 BIGINT       PRIMARY KEY AUTO_INCREMENT,
  username           VARCHAR(64)  NOT NULL,
  password_hash      VARCHAR(128) NOT NULL,
  enabled            TINYINT      NOT NULL DEFAULT 1,
  role               VARCHAR(32)  NOT NULL DEFAULT 'ADMIN',
  nickname           VARCHAR(64),
  password_changed_at DATETIME,
  deleted            TINYINT      NOT NULL DEFAULT 0,
  create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time        DATETIME,
  UNIQUE (username)
);

-- API Key 表（客户端 SDK 凭证）
CREATE TABLE IF NOT EXISTS api_key (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id        VARCHAR(128) NOT NULL,
  api_key_hash  VARCHAR(128) NOT NULL,
  app_name      VARCHAR(128),
  enabled       TINYINT      NOT NULL DEFAULT 1,
  expire_time   DATETIME,
  description   VARCHAR(256),
  deleted       TINYINT      NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME,
  UNIQUE (app_id)
);

-- 应用级线程池配置元数据（version 维度）
CREATE TABLE IF NOT EXISTS thread_pool_config_app (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id      VARCHAR(128) NOT NULL,
  version     BIGINT       NOT NULL DEFAULT 0,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME,
  UNIQUE (app_id)
);

-- 线程池配置明细表
CREATE TABLE IF NOT EXISTS thread_pool_config (
  id                       BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id                   VARCHAR(128) NOT NULL,
  version                  BIGINT       NOT NULL DEFAULT 0,
  pool_name                VARCHAR(128) NOT NULL,
  core_pool_size           INT          NOT NULL,
  maximum_pool_size        INT          NOT NULL,
  keep_alive_time          BIGINT       NOT NULL,
  time_unit                VARCHAR(32)  NOT NULL,
  queue_type               VARCHAR(64)  NOT NULL,
  queue_capacity           INT          NOT NULL,
  reject_policy_type       VARCHAR(32)  NOT NULL,
  allow_core_thread_timeout TINYINT     NOT NULL DEFAULT 0,
  thread_name_prefix       VARCHAR(64),
  daemon                   TINYINT      NOT NULL DEFAULT 0,
  deleted                  TINYINT      NOT NULL DEFAULT 0,
  create_time              DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time              DATETIME,
  UNIQUE (app_id, pool_name)
);

-- 线程池运行时统计上报表
CREATE TABLE IF NOT EXISTS thread_pool_stats (
  id                         BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id                     VARCHAR(128) NOT NULL,
  pool_name                  VARCHAR(128) NOT NULL,
  core_pool_size             INT          NOT NULL DEFAULT 0,
  maximum_pool_size          INT          NOT NULL DEFAULT 0,
  pool_size                  INT          NOT NULL DEFAULT 0,
  active_count               INT          NOT NULL DEFAULT 0,
  queue_size                 INT          NOT NULL DEFAULT 0,
  queue_capacity             INT          NOT NULL DEFAULT 0,
  queue_remaining_capacity   INT          NOT NULL DEFAULT 0,
  completed_task_count       BIGINT       NOT NULL DEFAULT 0,
  submitted_task_count       BIGINT       NOT NULL DEFAULT 0,
  error_task_count           BIGINT       NOT NULL DEFAULT 0,
  rejected_task_count        BIGINT       NOT NULL DEFAULT 0,
  largest_pool_size          INT          NOT NULL DEFAULT 0,
  task_count                 BIGINT       NOT NULL DEFAULT 0,
  is_shutdown                TINYINT      NOT NULL DEFAULT 0,
  is_terminated              TINYINT      NOT NULL DEFAULT 0,
  collect_time               DATETIME,
  deleted                    TINYINT      NOT NULL DEFAULT 0,
  create_time                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time                DATETIME
);

-- 操作审计日志（通用审计，记录"谁在何时做了什么"）
CREATE TABLE IF NOT EXISTS operate_log (
  id            BIGINT       PRIMARY KEY AUTO_INCREMENT,
  biz_id        VARCHAR(128),
  biz_type      VARCHAR(32),
  operate_type  VARCHAR(32),
  content       TEXT,
  operator      VARCHAR(64),
  deleted       TINYINT      NOT NULL DEFAULT 0,
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME
);

-- 配置历史快照表（版本快照链，不含操作类型——操作类型归审计日志 operate_log）
CREATE TABLE IF NOT EXISTS config_history (
  id           BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id       VARCHAR(128) NOT NULL,
  pool_name    VARCHAR(128) NOT NULL,
  version      BIGINT       NOT NULL,
  config_value TEXT,
  operator     VARCHAR(64),
  create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time  DATETIME,
  deleted      TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_app_pool_version (app_id, pool_name, version)
);