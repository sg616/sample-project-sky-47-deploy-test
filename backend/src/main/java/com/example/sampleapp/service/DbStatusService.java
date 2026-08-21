package com.example.sampleapp.service;

import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DbStatusService {

    private static final Logger log = LoggerFactory.getLogger(DbStatusService.class);

    private final DataSource dataSource;

    public DbStatusService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new HashMap<>();
        try (java.sql.Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                status.put("status", "UP");
                status.put("product", connection.getMetaData().getDatabaseProductName());
                status.put("version", connection.getMetaData().getDatabaseProductVersion());
            } else {
                status.put("status", "DOWN");
            }
        } catch (Exception e) {
            log.warn("Database health check failed: {}", e.getMessage());
            status.put("status", "DOWN");
        }
        return status;
    }
}
