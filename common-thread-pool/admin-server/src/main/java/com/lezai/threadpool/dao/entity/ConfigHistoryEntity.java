package com.lezai.threadpool.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lezai.threadpool.bean.ThreadPoolConfig;
import com.alibaba.fastjson2.JSON;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;

import java.io.Serial;

/**
 * 配置历史快照实体（对应 config_history 表）
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@TableName(value = "config_history", autoResultMap = true)
public class ConfigHistoryEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 7112123456789012345L;

    private String appId;
    private String poolName;
    private Long version;
    @TableField("config_value")
    private String value;
    private String operator;

    public ThreadPoolConfig getValueAsConfig() {
        return StringUtils.isBlank(value) ? null : JSON.parseObject(value, ThreadPoolConfig.class);
    }

    public void setValueFromConfig(ThreadPoolConfig config) {
        this.value = config == null ? null : JSON.toJSONString(config);
    }
}
