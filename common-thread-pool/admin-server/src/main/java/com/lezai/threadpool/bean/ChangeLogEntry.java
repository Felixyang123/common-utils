package com.lezai.threadpool.bean;

import com.lezai.threadpool.enums.ChangeType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 变更记录条目
 * 泛型类，支持存储不同类型实体的变更历史
 *
 * @param <T> 变更实体的类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangeLogEntry<T> {

    /**
     * 变更版本号
     * 每次变更递增，用于排序和追踪
     */
    private long version;

    /**
     * 变更类型
     */
    private ChangeType changeType;

    /**
     * 变更前的值（可选）
     * DELETE 和 UPDATE 操作时包含
     */
    private T oldValue;

    /**
     * 变更后的值
     * CREATE, UPDATE, REGENERATE 操作时包含
     */
    private T newValue;

    /**
     * 变更时间戳
     */
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    /**
     * 操作人（预留字段，用于未来扩展）
     */
    private String operator;

    /**
     * 创建新的变更记录
     *
     * @param version    版本号
     * @param changeType 变更类型
     * @param oldValue   旧值
     * @param newValue   新值
     * @param operator   操作人
     * @return 变更记录条目
     */
    public static <T> ChangeLogEntry<T> of(long version, ChangeType changeType, T oldValue, T newValue, String operator) {
        return ChangeLogEntry.<T>builder()
                .version(version)
                .changeType(changeType)
                .oldValue(oldValue)
                .newValue(newValue)
                .timestamp(LocalDateTime.now())
                .operator(operator)
                .build();
    }

    /**
     * 创建新的变更记录（无操作人）
     *
     * @param version    版本号
     * @param changeType 变更类型
     * @param oldValue   旧值
     * @param newValue   新值
     * @return 变更记录条目
     */
    public static <T> ChangeLogEntry<T> of(long version, ChangeType changeType, T oldValue, T newValue) {
        return of(version, changeType, oldValue, newValue, null);
    }
}
