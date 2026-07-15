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

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MyBatisConfigStorageTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("deleteConfigs triggers final listener notification before unregister")
    void deleteConfigsTriggersBeforeUnregister() {
        Cache<String, ThreadPoolConfigApp> cache = mock(Cache.class);
        ThreadPoolConfigPersistenceService configService = mock(ThreadPoolConfigPersistenceService.class);
        ThreadPoolConfigConverter converter = mock(ThreadPoolConfigConverter.class);
        ConfigChangeListenerManager listenerManager = mock(ConfigChangeListenerManager.class);
        when(configService.getConfigAppByAppId("app1"))
                .thenReturn(Optional.of(ThreadPoolConfigApp.builder().appId("app1").version(7L).build()))
                .thenReturn(Optional.empty());

        MyBatisConfigStorage storage = new MyBatisConfigStorage(cache, new LocalStripedLock(), configService, converter, listenerManager);

        storage.deleteConfigs("app1");

        InOrder inOrder = inOrder(configService, cache, listenerManager);
        inOrder.verify(configService).getConfigAppByAppId("app1");
        inOrder.verify(configService).deleteByAppId("app1");
        inOrder.verify(cache).remove("app1");
        inOrder.verify(listenerManager).triggerListeners("app1", 7L);
        inOrder.verify(listenerManager).unregister("app1");
    }
}
