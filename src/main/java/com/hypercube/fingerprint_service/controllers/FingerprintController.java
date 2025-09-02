package com.hypercube.fingerprint_service.controllers;

import com.hypercube.fingerprint_service.services.FingerprintDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/fingerprint")
@CrossOrigin(origins = "*")
public class FingerprintController {
    
    private static final Logger logger = LoggerFactory.getLogger(FingerprintController.class);
    
    @Autowired
    private FingerprintDeviceService deviceService;
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        return ResponseEntity.ok(Map.of(
            "status", "healthy",
            "service", "BIO600 Fingerprint Service",
            "platform_info", deviceService.getPlatformInfo(),
            "timestamp", System.currentTimeMillis()
        ));
    }
    
    /**
     * Initialize fingerprint device
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeDevice(
            @RequestParam(defaultValue = "0") int channel) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "channel", channel,
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            boolean success = deviceService.initializeDevice(channel);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Device initialized successfully",
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                String errorInfo = deviceService.getErrorInfo(-106); // Get info for error code -106
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Device initialization failed",
                    "channel", channel,
                    "error_code", -106,
                    "error_info", errorInfo,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.error("Error initializing device for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error initializing device: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Get device status
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getDeviceStatus() {
        try {
            Map<Integer, Boolean> status = deviceService.getDeviceStatus();
            return ResponseEntity.ok(Map.of(
                "success", true,
                "device_status", status,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            logger.error("Error getting device status: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error getting device status: " + e.getMessage(),
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Close device
     */
    @PostMapping("/close")
    public ResponseEntity<Map<String, Object>> closeDevice(
            @RequestParam(defaultValue = "0") int channel) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "channel", channel,
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            boolean success = deviceService.closeDevice(channel);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Device closed successfully",
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to close device",
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.error("Error closing device for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error closing device: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Get platform information
     */
    @GetMapping("/platform")
    public ResponseEntity<Map<String, Object>> getPlatformInfo() {
        return ResponseEntity.ok(Map.of(
            "success", true,
            "platform_info", deviceService.getPlatformInfo(),
            "supported", deviceService.isPlatformSupported(),
            "timestamp", System.currentTimeMillis()
        ));
    }
}
