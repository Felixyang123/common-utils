package com.lezai.samples.cache.sync;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Optional;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CacheSyncMessageImpl implements CacheSyncMessage {
    @Serial
    private static final long serialVersionUID = -5007085316172284909L;

    // JVM 级唯一标识，所有从本 JVM 发出的消息共享此 ID
    private static final String JVM_UNIQUE_ID = UUID.randomUUID().toString();

    private String category;

    private String key;

    private Long expireTime;

    // 消息创建者 JVM 的 UUID，随消息一起序列化传输
    private String sourceId;

    public CacheSyncMessageImpl(String category, String key, Long ttl) {
        this.category = category;
        this.key = key;
        this.expireTime = Optional.ofNullable(ttl).map(t -> t + System.currentTimeMillis()).orElse(null);
        this.sourceId = JVM_UNIQUE_ID;
    }

    @Override
    public String uniqueId() {
        return JVM_UNIQUE_ID;
    }
}
