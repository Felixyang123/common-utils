package com.lezai.threadpool.storage.remote;

import com.lezai.threadpool.TestDataFactory;
import com.lezai.threadpool.bean.ThreadPoolStats;
import com.lezai.threadpool.converter.ThreadPoolStatsConverter;
import com.lezai.threadpool.pojo.cmd.ThreadPoolStatsAddCmd;
import com.lezai.threadpool.pojo.dto.ThreadPoolStatsDto;
import com.lezai.threadpool.service.ThreadPoolStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MysqlStatsStorageTest {

    @Mock
    private ThreadPoolStatsService statsService;

    @Mock
    private ThreadPoolStatsConverter statsConverter;

    @Captor
    private ArgumentCaptor<ThreadPoolStatsAddCmd> cmdCaptor;

    private MysqlStatsStorage storage;

    @BeforeEach
    void setUp() {
        storage = new MysqlStatsStorage(statsService, statsConverter);
    }

    @Test
    @DisplayName("saveStats delegates to statsService with proper cmd")
    void saveStats() {
        ThreadPoolStats stats = TestDataFactory.defaultStats().build();

        storage.saveStats("app1", List.of(stats));

        verify(statsService).saveStatsApp(cmdCaptor.capture());
        ThreadPoolStatsAddCmd cmd = cmdCaptor.getValue();
        assertThat(cmd.getAppId()).isEqualTo("app1");
        assertThat(cmd.getStats()).hasSize(1);
    }

    @Test
    @DisplayName("saveStats with empty list does nothing")
    void saveStats_emptyList() {
        storage.saveStats("app1", List.of());

        verify(statsService, never()).saveStatsApp(any(ThreadPoolStatsAddCmd.class));
    }

    @Test
    @DisplayName("getPoolStatsHistory delegates to statsService and converts result")
    void getPoolStatsHistory() {
        ThreadPoolStatsDto dto = new ThreadPoolStatsDto();
        dto.setPoolName("test-pool");
        ThreadPoolStats expectedStats = TestDataFactory.defaultStats().build();
        LocalDateTime begin = LocalDateTime.now().minusMinutes(10);
        LocalDateTime end = LocalDateTime.now();

        when(statsService.queryStatsHistory("app1", "test-pool", begin, end)).thenReturn(List.of(dto));
        when(statsConverter.convertStatsBatch(List.of(dto))).thenReturn(List.of(expectedStats));

        List<ThreadPoolStats> result = storage.getPoolStatsHistory("app1", "test-pool", begin, end);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getPoolName()).isEqualTo("test-pool");
    }

    @Test
    @DisplayName("getPoolStatsHistory with null time range still delegates properly")
    void getPoolStatsHistory_nullTime() {
        when(statsService.queryStatsHistory("app1", "test-pool", null, null)).thenReturn(List.of());
        when(statsConverter.convertStatsBatch(List.of())).thenReturn(List.of());

        List<ThreadPoolStats> result = storage.getPoolStatsHistory("app1", "test-pool", null, null);

        assertThat(result).isEmpty();
    }
}
