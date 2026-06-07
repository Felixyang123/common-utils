package com.lezai.threadpool.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.io.Serial;

@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
@TableName(value = "thread_pool_config_app", autoResultMap = true)
public class ThreadPoolConfigAppEntity extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1927278964686944168L;

    private String appId;

    private Long version;
}
