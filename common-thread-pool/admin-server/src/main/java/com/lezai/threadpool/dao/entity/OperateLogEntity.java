package com.lezai.threadpool.dao.entity;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lezai.threadpool.enums.BizType;
import com.lezai.threadpool.enums.OperateType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.io.Serial;
import java.util.Optional;

/**
 * 操作日志条目
 *
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@SuperBuilder
@TableName(value = "operate_log", autoResultMap = true)
public class OperateLogEntity extends BaseEntity {
    @Serial
    private static final long serialVersionUID = 6336354883029529725L;

    /**
     * 日志 ID
     * bizType=APIKEY, bizId=APIKEY_ID
     * bizType=THREADPOOL, bizId=THREADPOOL_CONFIG_ID
     */
    private String bizId;

    /**
     * 日志类型
     *
     * @see BizType
     */
    private String bizType;

    /**
     * 变更类型
     *
     * @see com.lezai.threadpool.enums.OperateType
     */
    private String operateType;

    /**
     * 变更前的值（可选）
     * DELETE 和 UPDATE 操作时包含
     */
    private String content;

    /**
     * 操作人
     */
    private String operator;

    /**
     * 创建新的变更记录
     *
     * @param operateType 变更类型
     * @param operator    操作人
     * @return 变更记录条目
     */
    public static <T> OperateLogEntity of(OperateType operateType, T obj, String operator, String bizId, String bizType) {
        return OperateLogEntity.builder()
                .bizId(bizId)
                .bizType(bizType)
                .operateType(operateType.name())
                .content(Optional.ofNullable(obj).map(JSON::toJSONString).orElse(""))
                .operator(operator)
                .build();
    }
}
