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
}
