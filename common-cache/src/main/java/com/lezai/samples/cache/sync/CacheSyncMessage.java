package com.lezai.samples.cache.sync;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CacheSyncMessage implements Serializable {
    @Serial
    private static final long serialVersionUID = -5007085316172284909L;

    private String category;

    private String key;
}
