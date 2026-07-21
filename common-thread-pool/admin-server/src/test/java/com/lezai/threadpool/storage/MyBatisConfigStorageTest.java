package com.lezai.threadpool.storage;

import com.lezai.threadpool.converter.ThreadPoolConfigConverter;
import com.lezai.threadpool.pojo.bean.ThreadPoolConfigApp;
import com.lezai.threadpool.service.ThreadPoolConfigPersistenceService;
import com.lezai.threadpool.storage.cache.Cache;
import com.lezai.threadpool.storage.listener.ConfigChangeListenerManager;
import com.lezai.threadpool.util.LocalStripedLock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.Optional;

import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MyBatisConfigStorageTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("deleteConfigs triggers final listener notification with Long.MAX_VALUE (whole-app unmanage)")
    void deleteConfigsTriggersBeforeUnregister() {
        Cache<String, ThreadPoolConfigApp> cache = mock(Cache.class);
        ThreadPoolConfigPersistenceService configService = mock(ThreadPoolConfigPersistenceService.class);
        ThreadPoolConfigConverter converter = mock(ThreadPoolConfigConverter.class);
        ConfigChangeListenerManager listenerManager = mock(ConfigChangeListenerManager.class);

        MyBatisConfigStorage storage = new MyBatisConfigStorage(cache, new LocalStripedLock(), configService, converter, listenerManager);

        storage.deleteConfigs("app1");

        InOrder inOrder = inOrder(configService, cache, listenerManager);
        // 整 app 退管：不再读取当前版本，直接传 Long.MAX_VALUE 确保客户端 newVersion > version 成立
        inOrder.verify(configService).deleteByAppId("app1");
        inOrder.verify(cache).remove("app1");
        inOrder.verify(listenerManager).triggerListeners("app1", Long.MAX_VALUE);
        // triggerListeners 已整表 remove 监听器，不再冗余调用 unregister
        verify(listenerManager, never()).unregister(anyString());
    }
}
