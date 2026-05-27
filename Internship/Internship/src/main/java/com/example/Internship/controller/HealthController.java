package com.example.Internship.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
public class HealthController {

    private static final Logger logger = LoggerFactory.getLogger(HealthController.class);

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // ─── SIMPLE HEALTH CHECK ──────────────────────────────────────────────────
    // GET /health
    // NGINX / load balancer pings this to know app is alive
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", LocalDateTime.now().toString());
        response.put("service", "notes-app");

        // Check PostgreSQL
        String dbStatus = checkDatabase();
        response.put("database", dbStatus);

        // Check Redis
        String redisStatus = checkRedis();
        response.put("redis", redisStatus);

        // Overall status
        boolean allUp = "UP".equals(dbStatus) && "UP".equals(redisStatus);
        response.put("status", allUp ? "UP" : "DEGRADED");

        logger.info("Health check: DB={}, Redis={}", dbStatus, redisStatus);
        return ResponseEntity.ok(response);
    }

    // ─── SIMPLE PING ──────────────────────────────────────────────────────────
    // GET /ping
    @GetMapping("/ping")
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
            "message", "pong",
            "timestamp", LocalDateTime.now().toString()
        ));
    }

    // ─── PRIVATE HELPERS ──────────────────────────────────────────────────────

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            logger.error("Database health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }

    private String checkRedis() {
        try {
            redisTemplate.opsForValue().set("health:ping", "pong");
            redisTemplate.delete("health:ping");
            return "UP";
        } catch (Exception e) {
            logger.error("Redis health check failed: {}", e.getMessage());
            return "DOWN";
        }
    }
}
