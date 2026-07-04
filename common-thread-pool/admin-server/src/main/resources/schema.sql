-- ============================================================
-- admin-server schema (参考脚本，部署时手动执行)
-- ============================================================

-- 配置历史快照表（版本快照链，不含操作类型——操作类型归审计日志 operate_log）
CREATE TABLE IF NOT EXISTS config_history (
  id          BIGINT       PRIMARY KEY AUTO_INCREMENT,
  app_id      VARCHAR(128) NOT NULL,
  pool_name   VARCHAR(128) NOT NULL,
  version     BIGINT       NOT NULL,
  value       TEXT,
  operator    VARCHAR(64),
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted     TINYINT      NOT NULL DEFAULT 0,
  INDEX idx_app_pool_version (app_id, pool_name, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='线程池配置版本快照';
