package com.lezai.threadpool.open;

import com.lezai.threadpool.enums.HealthState;
import com.lezai.threadpool.pojo.response.HealthResponse;
import com.lezai.threadpool.service.AdminHealthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/open/api/thread-pool")
@RequiredArgsConstructor
public class HealthController {

    private final AdminHealthService adminHealthService;

    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse response = adminHealthService.check();
        HttpStatus status = response.getStatus() == HealthState.DOWN
                ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK;
        return ResponseEntity.status(status).body(response);
    }
}