CREATE TABLE IF NOT EXISTS idempotent_record (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    idempotent_key VARCHAR(255) NOT NULL COMMENT '幂等键',
    process_status TINYINT NOT NULL COMMENT '状态：1-执行中 2-执行成功 3-执行失败',
    process_result TEXT COMMENT '执行结果（可选存储）',
    result_type VARCHAR(255) COMMENT '结果类型',
    error_message TEXT COMMENT '错误信息',
    duration BIGINT COMMENT '执行耗时（毫秒）',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    UNIQUE KEY uk_idempotent_key (idempotent_key),
    INDEX idx_expire_time (expire_time),
    INDEX idx_create_time (create_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='幂等性记录表';
