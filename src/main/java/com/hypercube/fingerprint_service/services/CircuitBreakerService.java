package com.hypercube.fingerprint_service.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CircuitBreakerService {
    
    private static final Logger logger = LoggerFactory.getLogger(CircuitBreakerService.class);
    
    private final AtomicInteger failureCount = new AtomicInteger(0);
    private final AtomicLong lastFailureTime = new AtomicLong(0);
    private final AtomicLong lastSuccessTime = new AtomicLong(System.currentTimeMillis());
    
    private static final int FAILURE_THRESHOLD = 5;
    private static final long TIMEOUT_MS = 30000; // 30 seconds
    private static final long RECOVERY_TIME_MS = 60000; // 1 minute for recovery
    
    public enum CircuitState {
        CLOSED,    // Normal operation
        OPEN,      // Circuit is open, blocking requests
        HALF_OPEN  // Testing if service is recovering
    }
    
    private volatile CircuitState state = CircuitState.CLOSED;
    
    /**
     * Check if the circuit breaker allows the operation to proceed
     */
    public boolean isOperationAllowed() {
        switch (state) {
            case CLOSED:
                return true;
                
            case OPEN:
                // Check if enough time has passed to try again
                if (System.currentTimeMillis() - lastFailureTime.get() > TIMEOUT_MS) {
                    state = CircuitState.HALF_OPEN;
                    logger.info("Circuit breaker transitioning to HALF_OPEN state");
                    return true;
                }
                return false;
                
            case HALF_OPEN:
                // Allow limited operations to test recovery
                return true;
                
            default:
                return false;
        }
    }
    
    /**
     * Record a successful operation
     */
    public void recordSuccess() {
        lastSuccessTime.set(System.currentTimeMillis());
        
        if (state == CircuitState.HALF_OPEN) {
            // If we're in half-open and got a success, close the circuit
            state = CircuitState.CLOSED;
            failureCount.set(0);
            logger.info("Circuit breaker transitioning to CLOSED state after successful recovery");
        } else if (state == CircuitState.CLOSED) {
            // Reset failure count on success in closed state
            failureCount.set(0);
        }
    }
    
    /**
     * Record a failed operation
     */
    public void recordFailure() {
        failureCount.incrementAndGet();
        lastFailureTime.set(System.currentTimeMillis());
        
        if (failureCount.get() >= FAILURE_THRESHOLD) {
            state = CircuitState.OPEN;
            logger.warn("Circuit breaker OPENED due to {} consecutive failures", failureCount.get());
        }
    }
    
    /**
     * Get current circuit state
     */
    public CircuitState getState() {
        return state;
    }
    
    /**
     * Get failure count
     */
    public int getFailureCount() {
        return failureCount.get();
    }
    
    /**
     * Get time since last failure
     */
    public long getTimeSinceLastFailure() {
        return System.currentTimeMillis() - lastFailureTime.get();
    }
    
    /**
     * Get time since last success
     */
    public long getTimeSinceLastSuccess() {
        return System.currentTimeMillis() - lastSuccessTime.get();
    }
    
    /**
     * Force reset the circuit breaker (for manual recovery)
     */
    public void reset() {
        state = CircuitState.CLOSED;
        failureCount.set(0);
        lastFailureTime.set(0);
        lastSuccessTime.set(System.currentTimeMillis());
        logger.info("Circuit breaker manually reset");
    }
    
    /**
     * Get circuit breaker status information
     */
    public String getStatus() {
        return String.format("State: %s, Failures: %d, Last Failure: %dms ago, Last Success: %dms ago",
                state, failureCount.get(), getTimeSinceLastFailure(), getTimeSinceLastSuccess());
    }
}
