package com.lezai.threadpool.service;

import com.lezai.threadpool.enums.HealthState;
import com.lezai.threadpool.pojo.response.HealthResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminHealthService {

    private final JdbcTemplate jdbcTemplate;
    private final Environment environment;

    @Autowired(required = false)
    private RedissonClient redissonClient;

    public HealthResponse check() {
        HealthState db = checkDb();
        HealthState redis = checkRedis();
        HealthState status = overall(db, redis);
        return HealthResponse.builder()
                .status(status).db(db).redis(redis)
                .timestamp(LocalDateTime.now()).build();
    }

    private HealthState checkDb() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return Integer.valueOf(1).equals(result) ? HealthState.UP : HealthState.DOWN;
        } catch (Exception e) {
            log.warn("Admin health DB check failed", e);
            return HealthState.DOWN;
        }
    }

    private HealthState checkRedis() {
        if (isLocalProfile() || redissonClient == null) return HealthState.N_A;
        try {
            return redissonClient.getKeys().count() >= 0 ? HealthState.UP : HealthState.DOWN;
        } catch (Exception e) {
            log.warn("Admin health Redis check failed", e);
            return HealthState.DOWN;
        }
    }

    private HealthState overall(HealthState db, HealthState redis) {
        if (db == HealthState.DOWN) return HealthState.DOWN;
        if (redis == HealthState.DOWN) return HealthState.DEGRADED;
        return HealthState.UP;
    }

    private boolean isLocalProfile() {
        return Arrays.asList(environment.getActiveProfiles()).contains("local");
    }
}