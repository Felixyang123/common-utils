package com.lezai.samples.cache.sync;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.UUID;

@Data
@NoArgsConstructor
public class CacheSyncMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = -5007085316172284909L;

    public static final String uniqueId = UUID.randomUUID().toString();

    private String category;

    private String key;

    private Long expireTime;
    /**
     * 来源客户端 ID
     */
    private String sourceId = CacheSyncMessage.uniqueId;

    public CacheSyncMessage(String category, String key, Long expireTime) {
        this.category = category;
        this.key = key;
        this.expireTime = expireTime;
    }
}
