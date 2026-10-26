package com.example.sampleapp.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.sampleapp.service.DbStatusService;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final DbStatusService dbStatusService;

    public ApiController(DbStatusService dbStatusService) {
        this.dbStatusService = dbStatusService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        log.info("GET /api/health called");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        String env = System.getenv().getOrDefault("APP_ENV", "local");
        log.info("GET /api/info called, environment={}", env);
        Map<String, Object> response = new HashMap<>();
        response.put("app", "sample-backend");
        response.put("version", "1.0.0");
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("environment", env);
        response.put("database", dbStatusService.status());
        return response;
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody Map<String, Object> body) {
        log.info("POST /api/echo called, payload keys={}", body.keySet());
        Map<String, Object> response = new HashMap<>();
        response.put("received", body);
        response.put("timestamp", Instant.now().toString());
        return response;
    }
}
