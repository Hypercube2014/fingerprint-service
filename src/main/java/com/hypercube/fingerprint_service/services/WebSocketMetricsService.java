package com.hypercube.fingerprint_service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class WebSocketMetricsService {
    
    private static final Logger logger = LoggerFactory.getLogger(WebSocketMetricsService.class);
    
    // Connection metrics
    private final AtomicInteger totalConnections = new AtomicInteger(0);
    private final AtomicInteger activeConnections = new AtomicInteger(0);
    private final AtomicLong totalMessages = new AtomicLong(0);
    
    // Performance metrics
    private final AtomicLong totalProcessingTime = new AtomicLong(0);
    private final AtomicInteger slowOperations = new AtomicInteger(0);
    private final AtomicInteger blockedOperations = new AtomicInteger(0);
    
    // Per-session metrics
    private final ConcurrentHashMap<String, SessionMetrics> sessionMetrics = new ConcurrentHashMap<>();
    
    private static class SessionMetrics {
        final AtomicInteger messageCount = new AtomicInteger(0);
        final AtomicLong totalProcessingTime = new AtomicLong(0);
        final AtomicLong lastActivity = new AtomicLong(System.currentTimeMillis());
        final AtomicInteger slowOperations = new AtomicInteger(0);
    }
    
    /**
     * Record a new WebSocket connection
     */
    public void recordConnection(String sessionId) {
        totalConnections.incrementAndGet();
        activeConnections.incrementAndGet();
        sessionMetrics.put(sessionId, new SessionMetrics());
        logger.debug("New WebSocket connection: {} (Total: {})", sessionId, activeConnections.get());
    }
    
    /**
     * Record a WebSocket disconnection
     */
    public void recordDisconnection(String sessionId) {
        activeConnections.decrementAndGet();
        sessionMetrics.remove(sessionId);
        logger.debug("WebSocket disconnected: {} (Active: {})", sessionId, activeConnections.get());
    }
    
    /**
     * Record a message processing operation
     */
    public void recordMessageProcessing(String sessionId, String operation, long processingTimeMs) {
        totalMessages.incrementAndGet();
        totalProcessingTime.addAndGet(processingTimeMs);
        
        SessionMetrics metrics = sessionMetrics.get(sessionId);
        if (metrics != null) {
            metrics.messageCount.incrementAndGet();
            metrics.totalProcessingTime.addAndGet(processingTimeMs);
            metrics.lastActivity.set(System.currentTimeMillis());
            
            // Flag slow operations (>100ms)
            if (processingTimeMs > 100) {
                slowOperations.incrementAndGet();
                metrics.slowOperations.incrementAndGet();
                logger.warn("Slow operation detected: {} took {}ms for session {}", 
                           operation, processingTimeMs, sessionId);
            }
            
            // Flag blocked operations (>500ms)
            if (processingTimeMs > 500) {
                blockedOperations.incrementAndGet();
                logger.error("BLOCKED operation detected: {} took {}ms for session {}", 
                           operation, processingTimeMs, sessionId);
            }
        }
    }
    
    /**
     * Get current metrics summary
     */
    public MetricsSummary getMetricsSummary() {
        int activeSessions = activeConnections.get();
        long totalMsgs = totalMessages.get();
        long avgProcessingTime = totalMsgs > 0 ? totalProcessingTime.get() / totalMsgs : 0;
        
        return new MetricsSummary(
            totalConnections.get(),
            activeSessions,
            totalMsgs,
            avgProcessingTime,
            slowOperations.get(),
            blockedOperations.get(),
            sessionMetrics.size()
        );
    }
    
    /**
     * Check if system is healthy
     */
    public HealthStatus getHealthStatus() {
        int activeSessions = activeConnections.get();
        int slowOps = slowOperations.get();
        int blockedOps = blockedOperations.get();
        
        if (activeSessions > 20) {
            return HealthStatus.UNHEALTHY("Too many active connections: " + activeSessions);
        }
        
        if (blockedOps > 0) {
            return HealthStatus.UNHEALTHY("Blocked operations detected: " + blockedOps);
        }
        
        if (slowOps > 10) {
            return HealthStatus.DEGRADED("High number of slow operations: " + slowOps);
        }
        
        return HealthStatus.HEALTHY;
    }
    
    /**
     * Reset metrics (for testing or maintenance)
     */
    public void resetMetrics() {
        totalConnections.set(0);
        totalMessages.set(0);
        totalProcessingTime.set(0);
        slowOperations.set(0);
        blockedOperations.set(0);
        sessionMetrics.clear();
        logger.info("WebSocket metrics reset");
    }
    
    public static class MetricsSummary {
        public final int totalConnections;
        public final int activeConnections;
        public final long totalMessages;
        public final long avgProcessingTimeMs;
        public final int slowOperations;
        public final int blockedOperations;
        public final int activeSessions;
        
        public MetricsSummary(int totalConnections, int activeConnections, long totalMessages,
                            long avgProcessingTimeMs, int slowOperations, int blockedOperations, int activeSessions) {
            this.totalConnections = totalConnections;
            this.activeConnections = activeConnections;
            this.totalMessages = totalMessages;
            this.avgProcessingTimeMs = avgProcessingTimeMs;
            this.slowOperations = slowOperations;
            this.blockedOperations = blockedOperations;
            this.activeSessions = activeSessions;
        }
        
        @Override
        public String toString() {
            return String.format("Connections: %d active/%d total, Messages: %d, " +
                               "Avg Processing: %dms, Slow: %d, Blocked: %d",
                    activeConnections, totalConnections, totalMessages, avgProcessingTimeMs,
                    slowOperations, blockedOperations);
        }
    }
    
    public static class HealthStatus {
        public static final HealthStatus HEALTHY = new HealthStatus("HEALTHY", null);
        
        private final String status;
        private final String message;
        
        private HealthStatus(String status, String message) {
            this.status = status;
            this.message = message;
        }
        
        public static HealthStatus DEGRADED(String message) {
            return new HealthStatus("DEGRADED", message);
        }
        
        public static HealthStatus UNHEALTHY(String message) {
            return new HealthStatus("UNHEALTHY", message);
        }
        
        public String getMessage() {
            return message;
        }
        
        public String getStatus() {
            return status;
        }
        
        public boolean isHealthy() {
            return this == HEALTHY;
        }
        
        @Override
        public String toString() {
            return status + (message != null ? ": " + message : "");
        }
    }
}
