package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;
import com.lezai.idempotent.exception.IdempotentStorageException;

/**
 * 幂等性存储策略接口。
 *
 * <p><strong>接口契约 (参见 ADR-0002):</strong></p>
 * <ul>
 *   <li>get(key) 返回 {@code null} ⟹ 幂等键确定不存在。此语义为"硬保证",
 *       不允许实现类将 IO/反序列化异常包装为 null 返回。</li>
 *   <li>所有实现类在遇到存储访问异常时必须上抛非空异常(实现类可直接上抛
 *       {@code DataAccessException} 等具体异常,禁止包装为 null/false 返回),
 *       调用方 (IdempotentExecutionManager) 通过 catch(Exception) 统一包装为
 *       {@link IdempotentStorageException} 以实现 fail-fast 兜底
 *       (映射为 HTTP 503 + Retry-After 等)。</li>
 *   <li>save / remove 操作失败同样需要上抛异常,不可静默吞错。</li>
 *   <li>调用方 (keyResolver + manager) 负责保证 key / record 非 null,
 *       storage 实现遵循 "trust the caller" 原则,不加防御性 null 校验。</li>
 * </ul>
 *
 * <p>契约推论:"不知道" ≠ "无记录" —— 存储层必须明确区分这两类状态。</p>
 */
public interface IdempotentStorage {

    /**
     * 获取幂等记录。
     *
     * @param key 幂等键
     * @return 幂等记录,不存在返回 {@code null}(这是唯一合法的 "无记录" 信号)
     * @throws 存储访问异常(实现类可抛 DataAccessException 等具体异常,调用方负责包装为 IdempotentStorageException),调用方不得将此异常视为"无记录"
     */
    IdempotentRecord get(String key);

    /**
     * 保存幂等记录。
     *
     * @param record        幂等记录
     * @param expireSeconds 过期时间(秒)
     * @throws 存储访问异常(实现类可抛 DataAccessException 等具体异常,调用方负责包装为 IdempotentStorageException)
     */
    void save(IdempotentRecord record, long expireSeconds);

    /**
     * 删除幂等记录。
     *
     * @param key 幂等键
     * @throws 存储访问异常(实现类可抛 DataAccessException 等具体异常,调用方负责包装为 IdempotentStorageException)
     */
    void remove(String key);

    /**
     * 检查幂等记录是否存在。
     *
     * @param key 幂等键
     * @return 若现有记录对应此 key 且未过期则返回 {@code true}
     * @throws 存储访问异常(实现类可抛 DataAccessException 等具体异常,调用方负责包装为 IdempotentStorageException),不得视为 {@code false}
     */
    boolean exists(String key);
}
