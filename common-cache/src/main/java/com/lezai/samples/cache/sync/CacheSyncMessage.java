package com.lezai.samples.cache.sync;

import java.io.Serializable;

public interface CacheSyncMessage extends Serializable {

    String uniqueId();

    String getSourceId();
}
