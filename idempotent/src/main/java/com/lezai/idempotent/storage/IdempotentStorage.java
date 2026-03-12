package com.lezai.idempotent.storage;

import com.lezai.idempotent.core.IdempotentRecord;

/**
 * 幂等性存储策略接口
 * 专注于幂等记录的存储和检索
 */
public interface IdempotentStorage {

    /**
     * 获取幂等记录
     *
     * @param key 幂等键
     * @return 幂等记录，不存在返回 null
     */
    IdempotentRecord get(String key);

    /**
     * 保存幂等记录
     *
     * @param record        幂等记录
     * @param expireSeconds 过期时间（秒）
     */
    void save(IdempotentRecord record, long expireSeconds);

    /**
     * 删除幂等记录
     *
     * @param key 幂等键
     */
    void remove(String key);

    /**
     * 检查是否存在
     *
     * @param key 幂等键
     * @return 是否存在
     */
    boolean exists(String key);
}
