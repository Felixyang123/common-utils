package com.lezai.threadpool.open;

import com.lezai.threadpool.enums.HealthState;
import com.lezai.threadpool.pojo.response.HealthResponse;
import com.lezai.threadpool.service.AdminHealthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HealthControllerTest {

    @Mock private AdminHealthService adminHealthService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(adminHealthService)).build();
    }

    @Test
    @DisplayName("UP returns HTTP 200")
    void upReturns200() throws Exception {
        when(adminHealthService.check()).thenReturn(response(HealthState.UP, HealthState.UP, HealthState.UP));
        mockMvc.perform(get("/open/api/thread-pool/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("DEGRADED returns HTTP 200")
    void degradedReturns200() throws Exception {
        when(adminHealthService.check()).thenReturn(response(HealthState.DEGRADED, HealthState.UP, HealthState.DOWN));
        mockMvc.perform(get("/open/api/thread-pool/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DEGRADED"));
    }

    @Test
    @DisplayName("DOWN returns HTTP 503")
    void downReturns503() throws Exception {
        when(adminHealthService.check()).thenReturn(response(HealthState.DOWN, HealthState.DOWN, HealthState.N_A));
        mockMvc.perform(get("/open/api/thread-pool/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
    }

    private HealthResponse response(HealthState status, HealthState db, HealthState redis) {
        return HealthResponse.builder().status(status).db(db).redis(redis).timestamp(LocalDateTime.now()).build();
    }
}