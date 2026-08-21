package com.example.ordersapp.controller;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.ordersapp.service.DbStatusService;

@RestController
@RequestMapping
public class ApiController {

    private static final Logger log = LoggerFactory.getLogger(ApiController.class);

    private final DbStatusService dbStatusService;

    public ApiController(DbStatusService dbStatusService) {
        this.dbStatusService = dbStatusService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        log.info("GET /orders-api/health called");
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("service", "orders-backend");
        response.put("timestamp", Instant.now().toString());
        return response;
    }

    @GetMapping("/info")
    public Map<String, Object> info() {
        String env = System.getenv().getOrDefault("APP_ENV", "local");
        log.info("GET /orders-api/info called, environment={}", env);
        Map<String, Object> response = new HashMap<>();
        response.put("app", "orders-backend");
        response.put("version", "1.0.0");
        response.put("javaVersion", System.getProperty("java.version"));
        response.put("environment", env);
        response.put("database", dbStatusService.status());
        return response;
    }
}
