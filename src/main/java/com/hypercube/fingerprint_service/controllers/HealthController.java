package com.hypercube.fingerprint_service.controllers;

import com.hypercube.fingerprint_service.services.CircuitBreakerService;
import com.hypercube.fingerprint_service.services.WebSocketMetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private WebSocketMetricsService metricsService;

    @Autowired
    private CircuitBreakerService circuitBreaker;

    /**
     * Health check endpoint
     */
    @GetMapping
    public ResponseEntity<Map<String, Object>> health() {
        Map<String, Object> health = new HashMap<>();
        
        // Get metrics
        WebSocketMetricsService.MetricsSummary metrics = metricsService.getMetricsSummary();
        WebSocketMetricsService.HealthStatus healthStatus = metricsService.getHealthStatus();
        
        // Get circuit breaker status
        CircuitBreakerService.CircuitState circuitState = circuitBreaker.getState();
        
        // Overall health
        boolean isHealthy = healthStatus.isHealthy() && circuitState != CircuitBreakerService.CircuitState.OPEN;
        
        health.put("status", isHealthy ? "UP" : "DOWN");
        health.put("timestamp", System.currentTimeMillis());
        
        // WebSocket metrics
        Map<String, Object> websocket = new HashMap<>();
        websocket.put("active_connections", metrics.activeConnections);
        websocket.put("total_connections", metrics.totalConnections);
        websocket.put("total_messages", metrics.totalMessages);
        websocket.put("avg_processing_time_ms", metrics.avgProcessingTimeMs);
        websocket.put("slow_operations", metrics.slowOperations);
        websocket.put("blocked_operations", metrics.blockedOperations);
        health.put("websocket", websocket);
        
        // Circuit breaker status
        Map<String, Object> circuitBreaker = new HashMap<>();
        circuitBreaker.put("state", circuitState.toString());
        circuitBreaker.put("failure_count", this.circuitBreaker.getFailureCount());
        circuitBreaker.put("time_since_last_failure_ms", this.circuitBreaker.getTimeSinceLastFailure());
        circuitBreaker.put("time_since_last_success_ms", this.circuitBreaker.getTimeSinceLastSuccess());
        health.put("circuit_breaker", circuitBreaker);
        
        // Health status details
        if (!healthStatus.isHealthy()) {
            health.put("issues", healthStatus.getMessage());
        }
        
        return ResponseEntity.status(isHealthy ? 200 : 503).body(health);
    }

    /**
     * Metrics endpoint
     */
    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> metrics() {
        WebSocketMetricsService.MetricsSummary metrics = metricsService.getMetricsSummary();
        CircuitBreakerService.CircuitState circuitState = circuitBreaker.getState();
        
        Map<String, Object> response = new HashMap<>();
        response.put("metrics", metrics);
        response.put("circuit_breaker", circuitBreaker.getStatus());
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }

    /**
     * Reset circuit breaker (for maintenance)
     */
    @GetMapping("/reset-circuit-breaker")
    public ResponseEntity<Map<String, Object>> resetCircuitBreaker() {
        circuitBreaker.reset();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "Circuit breaker reset successfully");
        response.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(response);
    }
}
