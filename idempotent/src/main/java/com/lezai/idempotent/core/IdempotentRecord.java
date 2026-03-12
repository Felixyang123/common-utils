package com.lezai.idempotent.core;

import com.lezai.idempotent.enums.IdempotentStatus;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 幂等记录实体
 * 存储幂等请求的核心信息
 */
@Data
public class IdempotentRecord implements Serializable {
    @Serial
    private static final long serialVersionUID = 3999865260251195622L;

    /**
     * 幂等键
     */
    private String key;
    
    /**
     * 执行状态
     */
    private IdempotentStatus status;
    
    /**
     * 执行结果（可选）
     */
    private String result;
    
    /**
     * 结果类型（用于反序列化）
     */
    private String resultType;
    
    /**
     * 异常信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private LocalDateTime createTime;
    
    /**
     * 更新时间
     */
    private LocalDateTime updateTime;
    
    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
    
    /**
     * 执行耗时（毫秒）
     */
    private Long duration;
}
