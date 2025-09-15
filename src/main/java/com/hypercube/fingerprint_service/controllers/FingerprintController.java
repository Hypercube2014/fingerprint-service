package com.hypercube.fingerprint_service.controllers;

import com.hypercube.fingerprint_service.services.FingerprintDeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Date;
import java.util.Map;
import java.util.HashMap;

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
     * Get/Capture fingerprint image - Main endpoint for taking fingerprint pictures
     */
    @PostMapping("/capture")
    public ResponseEntity<Map<String, Object>> captureFingerprint(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
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
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Capture the fingerprint image
            Map<String, Object> captureResult = deviceService.captureFingerprint(channel, width, height);
            
            if ((Boolean) captureResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Fingerprint captured successfully");
                response.put("width", width);
                response.put("height", height);
                response.put("quality_score", captureResult.get("quality_score"));
                response.put("channel", channel);
                response.put("captured_at", new Date());
                response.put("storage_info", Map.of(
                    "stored", captureResult.get("storage_success"),
                    "file_path", captureResult.get("file_path"),
                    "filename", captureResult.get("filename"),
                    "file_size", captureResult.get("file_size")
                ));
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to capture fingerprint",
                    "error_details", captureResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error capturing fingerprint for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error capturing fingerprint: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Simple GET endpoint for testing fingerprint capture (same functionality as POST /capture)
     */
    @GetMapping("/capture")
    public ResponseEntity<Map<String, Object>> captureFingerprintGet(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
        // Reuse the POST endpoint logic
        return captureFingerprint(channel, width, height);
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
    
    /**
     * Test image storage with different formats
     */
    @PostMapping("/storage/test")
    public ResponseEntity<Map<String, Object>> testImageStorage(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height,
            @RequestParam(defaultValue = "PNG") String format) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Capture the fingerprint image
            Map<String, Object> captureResult = deviceService.captureFingerprint(channel, width, height);
            
            if ((Boolean) captureResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Fingerprint captured and stored as " + format + " successfully");
                response.put("image_format", format);
                response.put("width", width);
                response.put("height", height);
                response.put("quality_score", captureResult.get("quality_score"));
                response.put("channel", channel);
                response.put("captured_at", new Date());
                response.put("storage_info", Map.of(
                    "stored", captureResult.get("storage_success"),
                    "file_path", captureResult.get("file_path"),
                    "filename", captureResult.get("filename"),
                    "file_size", captureResult.get("file_size")
                ));
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to capture fingerprint",
                    "error_details", captureResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error testing image storage for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error testing image storage: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Split Two Thumbs - Extract left and right thumb fingerprints from a single image
     * This endpoint captures an image containing both thumbs and automatically splits them
     */
    @PostMapping("/split/thumbs")
    public ResponseEntity<Map<String, Object>> splitTwoThumbs(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height,
            @RequestParam(defaultValue = "300") int splitWidth,
            @RequestParam(defaultValue = "400") int splitHeight) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Split the two thumbs using the device service
            Map<String, Object> splitResult = deviceService.splitTwoThumbs(
                channel, width, height, splitWidth, splitHeight);
            
            if ((Boolean) splitResult.get("success")) {
                return ResponseEntity.ok(splitResult);
            } else {
                return ResponseEntity.status(500).body(splitResult);
            }
            
        } catch (Exception e) {
            logger.error("Error splitting two thumbs for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error splitting two thumbs: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Test single capture and quality check (for debugging split issues)
     */
    @PostMapping("/test/capture-quality")
    public ResponseEntity<Map<String, Object>> testCaptureAndQuality(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            Map<String, Object> testResult = deviceService.testCaptureAndQuality(channel, width, height);
            return ResponseEntity.ok(testResult);
            
        } catch (Exception e) {
            logger.error("Error testing capture and quality: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error testing capture and quality: " + e.getMessage(),
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Test FPSPLIT library initialization with different dimensions
     * This helps debug FPSPLIT initialization issues
     */
    @GetMapping("/test/fpsplit")
    public ResponseEntity<Map<String, Object>> testFpSplitInitialization() {
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            Map<String, Object> testResult = deviceService.testFpSplitInitialization();
            return ResponseEntity.ok(testResult);
            
        } catch (Exception e) {
            logger.error("Error testing FPSPLIT initialization: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error testing FPSPLIT initialization: " + e.getMessage(),
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Test template generation with relaxed quality requirements
     * This endpoint helps test template generation even with lower quality scores
     */
    @PostMapping("/template/test-iso")
    public ResponseEntity<Map<String, Object>> testISOTemplate(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Test template generation with relaxed quality requirements
            Map<String, Object> templateResult = deviceService.testFingerprintTemplate(channel, width, height, "ISO");
            
            if ((Boolean) templateResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Fingerprint ISO template created successfully (test mode)");
                response.put("template_format", "ISO");
                response.put("template_size", templateResult.get("template_size"));
                response.put("template_data", templateResult.get("template_data"));
                response.put("quality_score", templateResult.get("quality_score"));
                response.put("width", width);
                response.put("height", height);
                response.put("channel", channel);
                response.put("captured_at", new Date());
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to create fingerprint template (test mode)",
                    "error_details", templateResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error testing ISO template for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error testing ISO template: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Validate image quality without capturing
     * This endpoint helps debug quality issues by testing the quality assessment methods
     */
    @PostMapping("/quality/validate")
    public ResponseEntity<Map<String, Object>> validateQuality(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Test quality validation
            Map<String, Object> qualityResult = deviceService.validateImageQuality(channel, width, height);
            return ResponseEntity.ok(qualityResult);
            
        } catch (Exception e) {
            logger.error("Error validating image quality for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error validating image quality: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Get storage statistics
     */
    @GetMapping("/storage/stats")
    public ResponseEntity<Map<String, Object>> getStorageStats() {
        try {
            // This would require injecting the storage service into the controller
            // For now, we'll return a placeholder response
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Storage statistics endpoint - requires storage service integration",
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            logger.error("Error getting storage statistics: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error getting storage statistics: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * List stored fingerprint images
     */
    @GetMapping("/storage/list")
    public ResponseEntity<Map<String, Object>> listStoredImages(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        
        try {
            // This would require injecting the storage service into the controller
            // For now, we'll return a placeholder response
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "List stored images endpoint - requires storage service integration",
                "page", page,
                "size", size,
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            logger.error("Error listing stored images: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error listing stored images: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Play sound feedback
     * @param soundType 1 = single beep (success), 2 = double beep (two-thumb mode), 3 = error beep
     */
    @PostMapping("/sound")
    public ResponseEntity<Map<String, Object>> playSound(
            @RequestParam(defaultValue = "1") int soundType) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            boolean success = deviceService.playSound(soundType);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Sound played successfully",
                    "sound_type", soundType,
                    "sound_description", getSoundDescription(soundType),
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to play sound",
                    "sound_type", soundType,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.error("Error playing sound, type {}: {}", soundType, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error playing sound: " + e.getMessage(),
                "sound_type", soundType,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Get sound description for sound type
     */
    private String getSoundDescription(int soundType) {
        switch (soundType) {
            case 1: return "Single beep (success)";
            case 2: return "Double beep (two-thumb mode)";
            case 3: return "Error beep";
            default: return "Unknown sound type";
        }
    }
    
    /**
     * Start real-time fingerprint preview stream
     * This endpoint starts a continuous stream of fingerprint images for real-time preview
     */
    @PostMapping("/preview/start")
    public ResponseEntity<Map<String, Object>> startPreview(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Start the preview stream
            boolean success = deviceService.startPreviewStream(channel, width, height);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Preview stream started successfully",
                    "channel", channel,
                    "width", width,
                    "height", height,
                    "stream_id", "preview_" + channel + "_" + System.currentTimeMillis(),
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to start preview stream",
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.error("Error starting preview stream for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error starting preview stream: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Stop real-time fingerprint preview stream
     */
    @PostMapping("/preview/stop")
    public ResponseEntity<Map<String, Object>> stopPreview(
            @RequestParam(defaultValue = "0") int channel) {
        
        try {
            boolean success = deviceService.stopPreviewStream(channel);
            
            if (success) {
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Preview stream stopped successfully",
                    "channel", channel,
                    "timestamp", System.currentTimeMillis()
                ));
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to stop preview stream",
                    "channel", channel,
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.error("Error stopping preview stream for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error stopping preview stream: " + e.getMessage(),
                "channel", channel,
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Get current preview frame (for polling-based approach)
     */
    @GetMapping("/preview/frame")
    public ResponseEntity<Map<String, Object>> getPreviewFrame(
            @RequestParam(defaultValue = "0") int channel) {
        
        try {
            Map<String, Object> frameData = deviceService.getCurrentPreviewFrame(channel);
            
            if (frameData != null && (Boolean) frameData.get("success")) {
                return ResponseEntity.ok(frameData);
            } else {
                return ResponseEntity.status(404).body(Map.of(
                    "success", false,
                    "message", "No preview frame available",
                    "channel", channel,
                    "timestamp", System.currentTimeMillis()
                ));
            }
        } catch (Exception e) {
            logger.error("Error getting preview frame for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error getting preview frame: " + e.getMessage(),
                "channel", channel,
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Capture fingerprint template (ISO format)
     * This endpoint captures a fingerprint image and generates an ISO template from it
     */
    @PostMapping("/template/iso")
    public ResponseEntity<Map<String, Object>> captureISOTemplate(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
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
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Capture the fingerprint template
            Map<String, Object> templateResult = deviceService.captureFingerprintTemplate(channel, width, height, "ISO");
            
            if ((Boolean) templateResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Fingerprint ISO template captured successfully");
                response.put("template_format", "ISO");
                response.put("template_size", templateResult.get("template_size"));
                response.put("template_data", templateResult.get("template_data"));
                response.put("quality_score", templateResult.get("quality_score"));
                response.put("width", width);
                response.put("height", height);
                response.put("channel", channel);
                response.put("captured_at", new Date());
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to capture fingerprint template",
                    "error_details", templateResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error capturing ISO template for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error capturing ISO template: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Capture fingerprint template (ANSI format)
     * This endpoint captures a fingerprint image and generates an ANSI template from it
     */
    @PostMapping("/template/ansi")
    public ResponseEntity<Map<String, Object>> captureANSITemplate(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
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
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Capture the fingerprint template
            Map<String, Object> templateResult = deviceService.captureFingerprintTemplate(channel, width, height, "ANSI");
            
            if ((Boolean) templateResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Fingerprint ANSI template captured successfully");
                response.put("template_format", "ANSI");
                response.put("template_size", templateResult.get("template_size"));
                response.put("template_data", templateResult.get("template_data"));
                response.put("quality_score", templateResult.get("quality_score"));
                response.put("width", width);
                response.put("height", height);
                response.put("channel", channel);
                response.put("captured_at", new Date());
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to capture fingerprint template",
                    "error_details", templateResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error capturing ANSI template for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error capturing ANSI template: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Capture fingerprint template (both ISO and ANSI formats)
     * This endpoint captures a fingerprint image and generates both ISO and ANSI templates
     */
    @PostMapping("/template/both")
    public ResponseEntity<Map<String, Object>> captureBothTemplates(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height) {
        
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
            
            // Check if device is initialized
            if (!deviceService.isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = deviceService.initializeDevice(channel);
                if (!initSuccess) {
                    return ResponseEntity.status(500).body(Map.of(
                        "success", false,
                        "message", "Device not initialized and initialization failed",
                        "channel", channel,
                        "platform_info", deviceService.getPlatformInfo(),
                        "timestamp", System.currentTimeMillis()
                    ));
                }
            }
            
            // Capture both templates
            Map<String, Object> templateResult = deviceService.captureFingerprintTemplate(channel, width, height, "BOTH");
            
            if ((Boolean) templateResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Fingerprint templates captured successfully");
                response.put("iso_template", templateResult.get("iso_template"));
                response.put("ansi_template", templateResult.get("ansi_template"));
                response.put("template_size", templateResult.get("template_size"));
                response.put("quality_score", templateResult.get("quality_score"));
                response.put("width", width);
                response.put("height", height);
                response.put("channel", channel);
                response.put("captured_at", new Date());
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to capture fingerprint templates",
                    "error_details", templateResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error capturing both templates for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error capturing both templates: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Compare two fingerprint templates
     * This endpoint compares two templates and returns a similarity score
     */
    @PostMapping("/template/compare")
    public ResponseEntity<Map<String, Object>> compareTemplates(
            @RequestParam("template1") String template1Base64,
            @RequestParam("template2") String template2Base64,
            @RequestParam(defaultValue = "0") int channel) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Compare the templates
            Map<String, Object> compareResult = deviceService.compareFingerprintTemplates(
                template1Base64, template2Base64, channel);
            
            if ((Boolean) compareResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Template comparison completed successfully");
                response.put("similarity_score", compareResult.get("similarity_score"));
                response.put("match_threshold", compareResult.get("match_threshold"));
                response.put("is_match", compareResult.get("is_match"));
                response.put("channel", channel);
                response.put("compared_at", new Date());
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to compare templates",
                    "error_details", compareResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error comparing templates for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error comparing templates: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
    
    /**
     * Search for a template in a collection of templates
     * This endpoint searches for a template in an array of stored templates
     */
    @PostMapping("/template/search")
    public ResponseEntity<Map<String, Object>> searchTemplates(
            @RequestParam("search_template") String searchTemplateBase64,
            @RequestParam("template_array") String templateArrayBase64,
            @RequestParam("array_count") int arrayCount,
            @RequestParam(defaultValue = "50") int matchThreshold,
            @RequestParam(defaultValue = "0") int channel) {
        
        try {
            // Check platform compatibility first
            if (!deviceService.isPlatformSupported()) {
                return ResponseEntity.status(400).body(Map.of(
                    "success", false,
                    "message", "Platform not supported. This SDK requires Windows.",
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
            // Search the templates
            Map<String, Object> searchResult = deviceService.searchFingerprintTemplates(
                searchTemplateBase64, templateArrayBase64, arrayCount, matchThreshold, channel);
            
            if ((Boolean) searchResult.get("success")) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Template search completed successfully");
                response.put("search_result", searchResult.get("search_result"));
                response.put("best_match_score", searchResult.get("best_match_score"));
                response.put("match_found", searchResult.get("match_found"));
                response.put("match_threshold", matchThreshold);
                response.put("array_count", arrayCount);
                response.put("channel", channel);
                response.put("searched_at", new Date());
                response.put("platform_info", deviceService.getPlatformInfo());
                response.put("timestamp", System.currentTimeMillis());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(500).body(Map.of(
                    "success", false,
                    "message", "Failed to search templates",
                    "error_details", searchResult.get("error_details"),
                    "channel", channel,
                    "platform_info", deviceService.getPlatformInfo(),
                    "timestamp", System.currentTimeMillis()
                ));
            }
            
        } catch (Exception e) {
            logger.error("Error searching templates for channel {}: {}", channel, e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of(
                "success", false,
                "message", "Error searching templates: " + e.getMessage(),
                "channel", channel,
                "platform_info", deviceService.getPlatformInfo(),
                "timestamp", System.currentTimeMillis()
            ));
        }
    }
}
