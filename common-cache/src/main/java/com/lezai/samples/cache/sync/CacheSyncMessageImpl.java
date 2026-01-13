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

    public static final String uniqueId = UUID.randomUUID().toString();

    private String category;

    private String key;

    private Long expireTime;
    /**
     * 来源客户端 ID
     */
    private String sourceId;

    public CacheSyncMessageImpl(String category, String key, Long ttl) {
        this.category = category;
        this.key = key;
        this.expireTime = Optional.ofNullable(ttl).map(t -> t + System.currentTimeMillis()).orElse(null);
        this.sourceId = uniqueId();
    }

    @Override
    public String uniqueId() {
        return CacheSyncMessageImpl.uniqueId;
    }
}
