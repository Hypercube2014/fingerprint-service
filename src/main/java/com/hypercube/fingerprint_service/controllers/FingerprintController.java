package com.hypercube.fingerprint_service.controllers;

import com.hypercube.fingerprint_service.services.FingerprintDeviceService;
import com.hypercube.fingerprint_service.services.FingerprintFileStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/fingerprint")
@CrossOrigin(origins = "*")
public class FingerprintController {
    
    private static final Logger logger = LoggerFactory.getLogger(FingerprintController.class);
    
    private final FingerprintDeviceService deviceService;
    private final FingerprintFileStorageService fileStorageService;
    
    @Autowired
    public FingerprintController(FingerprintDeviceService deviceService, FingerprintFileStorageService fileStorageService) {
        this.deviceService = deviceService;
        this.fileStorageService = fileStorageService;
    }
    
    /**
     * Initialize fingerprint device
     */
    @PostMapping("/init")
    public ResponseEntity<Map<String, Object>> initializeDevice(
            @RequestParam(defaultValue = "0") int channel) {
        
        logger.info("Initializing fingerprint device for channel: {}", channel);
        
        try {
            FingerprintDeviceService.DeviceInitResult result = deviceService.initializeDevice(channel);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("device_id", result.getChannel());
                response.put("timestamp", new Date());
                
                if (result.getDeviceInfo() != null) {
                    Map<String, Object> deviceInfo = new HashMap<>();
                    deviceInfo.put("channel_count", result.getDeviceInfo().getChannelCount());
                    deviceInfo.put("max_width", result.getDeviceInfo().getMaxWidth());
                    deviceInfo.put("max_height", result.getDeviceInfo().getMaxHeight());
                    deviceInfo.put("version", result.getDeviceInfo().getVersion());
                    deviceInfo.put("description", result.getDeviceInfo().getDescription());
                    response.put("device_info", deviceInfo);
                }
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("error_code", -1);
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error initializing device for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error initializing device: " + e.getMessage());
            response.put("error_code", -1);
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Capture fingerprint image with custom naming
     */
    @PostMapping("/capture")
    public ResponseEntity<Map<String, Object>> captureFingerprint(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height,
            @RequestParam(required = false) String customName) {
        
        logger.info("Capturing fingerprint for channel: {} with dimensions: {}x{} and custom name: {}", 
                   channel, width, height, customName);
        
        try {
            FingerprintDeviceService.CaptureResult result = deviceService.captureFingerprint(channel, width, height, customName);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("image", Base64.getEncoder().encodeToString(result.getImageData()));
                response.put("width", result.getWidth());
                response.put("height", result.getHeight());
                response.put("quality_score", result.getQuality());
                response.put("channel", result.getChannel());
                response.put("captured_at", new Date());
                
                // Add file storage information
                if (result.getStorageResult() != null && result.getStorageResult().isSuccess()) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("file_path", result.getStorageResult().getFilePath());
                    fileInfo.put("filename", result.getStorageResult().getFilename());
                    fileInfo.put("file_size", result.getStorageResult().getFileSize());
                    fileInfo.put("image_type", result.getStorageResult().getImageType());
                    response.put("file_info", fileInfo);
                }
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error capturing fingerprint for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error capturing fingerprint: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Capture high-resolution original image with custom naming
     */
    @PostMapping("/capture/original")
    public ResponseEntity<Map<String, Object>> captureOriginalImage(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(required = false) String customName) {
        
        logger.info("Capturing original image for channel: {} with custom name: {}", channel, customName);
        
        try {
            FingerprintDeviceService.CaptureResult result = deviceService.captureOriginalImage(channel, customName);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("image", Base64.getEncoder().encodeToString(result.getImageData()));
                response.put("width", result.getWidth());
                response.put("height", result.getHeight());
                response.put("channel", result.getChannel());
                response.put("captured_at", new Date());
                
                // Add file storage information
                if (result.getStorageResult() != null && result.getStorageResult().isSuccess()) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("file_path", result.getStorageResult().getFilePath());
                    fileInfo.put("filename", result.getStorageResult().getFilename());
                    fileInfo.put("file_size", result.getStorageResult().getFileSize());
                    fileInfo.put("image_type", result.getStorageResult().getImageType());
                    response.put("file_info", fileInfo);
                }
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error capturing original image for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error capturing original image: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Capture rolled fingerprint (stitched) with custom naming
     */
    @PostMapping("/capture/rolled")
    public ResponseEntity<Map<String, Object>> captureRolledFingerprint(
            @RequestParam(defaultValue = "800") int width,
            @RequestParam(defaultValue = "750") int height,
            @RequestParam(required = false) String customName) {
        
        logger.info("Capturing rolled fingerprint with dimensions: {}x{} and custom name: {}", width, height, customName);
        
        try {
            FingerprintDeviceService.CaptureResult result = deviceService.captureRolledFingerprint(width, height, customName);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("image", Base64.getEncoder().encodeToString(result.getImageData()));
                response.put("width", result.getWidth());
                response.put("height", result.getHeight());
                response.put("type", "rolled");
                response.put("captured_at", new Date());
                
                // Add file storage information
                if (result.getStorageResult() != null && result.getStorageResult().isSuccess()) {
                    Map<String, Object> fileInfo = new HashMap<>();
                    fileInfo.put("file_path", result.getStorageResult().getFilePath());
                    fileInfo.put("filename", result.getStorageResult().getFilename());
                    fileInfo.put("file_size", result.getStorageResult().getFileSize());
                    fileInfo.put("image_type", result.getStorageResult().getImageType());
                    response.put("file_info", fileInfo);
                }
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error capturing rolled fingerprint", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error capturing rolled fingerprint: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Split multiple fingers from image with custom naming
     */
    @PostMapping("/split")
    public ResponseEntity<Map<String, Object>> splitFingerprints(
            @RequestParam("image") String base64Image,
            @RequestParam(defaultValue = "1600") int width,
            @RequestParam(defaultValue = "1500") int height,
            @RequestParam(defaultValue = "300") int splitWidth,
            @RequestParam(defaultValue = "400") int splitHeight,
            @RequestParam(required = false) String customName) {
        
        logger.info("Splitting fingerprints with dimensions: {}x{} into {}x{} with custom name: {}", 
                   width, height, splitWidth, splitHeight, customName);
        
        try {
            byte[] imgBuf = Base64.getDecoder().decode(base64Image);
            
            FingerprintDeviceService.SplitResult result = deviceService.splitFingerprints(imgBuf, width, height, splitWidth, splitHeight, customName);
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("fingerprint_count", result.getFingerprintCount());
                response.put("fingerprints", result.getFingerprints());
                response.put("split_width", splitWidth);
                response.put("split_height", splitHeight);
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error splitting fingerprints", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error splitting fingerprints: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Store fingerprint image with custom path and naming
     */
    @PostMapping("/storage/store")
    public ResponseEntity<Map<String, Object>> storeFingerprintImage(
            @RequestParam("image") String base64Image,
            @RequestParam("image_type") String imageType,
            @RequestParam(required = false) String customName,
            @RequestParam(required = false) String customPath) {
        
        logger.info("Storing fingerprint image of type: {} with custom name: {} and custom path: {}", 
                   imageType, customName, customPath);
        
        try {
            byte[] imgBuf = Base64.getDecoder().decode(base64Image);
            
            FingerprintFileStorageService.FileStorageResult result;
            if (customPath != null && !customPath.trim().isEmpty()) {
                result = fileStorageService.storeFingerprintImageCustom(imgBuf, customPath, customName);
            } else {
                result = fileStorageService.storeFingerprintImage(imgBuf, imageType, customName);
            }
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("file_path", result.getFilePath());
                response.put("filename", result.getFilename());
                response.put("file_size", result.getFileSize());
                response.put("image_type", result.getImageType());
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error storing fingerprint image", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error storing fingerprint image: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Store fingerprint image in organized structure
     */
    @PostMapping("/storage/store/organized")
    public ResponseEntity<Map<String, Object>> storeFingerprintImageOrganized(
            @RequestParam("image") String base64Image,
            @RequestParam("image_type") String imageType,
            @RequestParam(required = false) String customName) {
        
        logger.info("Storing fingerprint image in organized structure, type: {} with custom name: {}", 
                   imageType, customName);
        
        try {
            byte[] imgBuf = Base64.getDecoder().decode(base64Image);
            
            FingerprintFileStorageService.FileStorageResult result = fileStorageService.storeFingerprintImageOrganized(
                imgBuf, imageType, customName
            );
            
            if (result.isSuccess()) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", result.getMessage());
                response.put("file_path", result.getFilePath());
                response.put("filename", result.getFilename());
                response.put("file_size", result.getFileSize());
                response.put("image_type", result.getImageType());
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", result.getMessage());
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error storing fingerprint image in organized structure", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error storing fingerprint image: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get file information
     */
    @GetMapping("/storage/file/info")
    public ResponseEntity<Map<String, Object>> getFileInfo(
            @RequestParam("file_path") String filePath) {
        
        logger.info("Getting file info for: {}", filePath);
        
        try {
            FingerprintFileStorageService.FileInfo fileInfo = fileStorageService.getFileInfo(filePath);
            
            if (fileInfo != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("file_path", fileInfo.getFilePath());
                response.put("filename", fileInfo.getFilename());
                response.put("size", fileInfo.getSize());
                response.put("last_modified", fileInfo.getLastModified());
                response.put("content_type", fileInfo.getContentType());
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "File not found");
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(404).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error getting file info for: {}", filePath, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting file info: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Delete fingerprint image
     */
    @DeleteMapping("/storage/file/delete")
    public ResponseEntity<Map<String, Object>> deleteFingerprintImage(
            @RequestParam("file_path") String filePath) {
        
        logger.info("Deleting fingerprint image: {}", filePath);
        
        try {
            boolean success = fileStorageService.deleteFingerprintImage(filePath);
            
            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "File deleted successfully");
                response.put("file_path", filePath);
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "File not found or could not be deleted");
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(404).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error deleting fingerprint image: {}", filePath, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error deleting file: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get storage statistics
     */
    @GetMapping("/storage/stats")
    public ResponseEntity<Map<String, Object>> getStorageStats() {
        logger.info("Getting storage statistics");
        
        try {
            FingerprintFileStorageService.StorageStats stats = fileStorageService.getStorageStats();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("total_files", stats.getTotalFiles());
            response.put("total_size", stats.getTotalSize());
            response.put("directory_count", stats.getDirectoryCount());
            response.put("timestamp", new Date());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting storage statistics", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting storage statistics: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Clean up old files
     */
    @PostMapping("/storage/cleanup")
    public ResponseEntity<Map<String, Object>> cleanupOldFiles(
            @RequestParam(defaultValue = "90") int daysToKeep) {
        
        logger.info("Cleaning up old files, keeping files newer than {} days", daysToKeep);
        
        try {
            FingerprintFileStorageService.CleanupResult result = fileStorageService.cleanupOldFiles(daysToKeep);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("deleted_files", result.getDeletedFiles());
            response.put("freed_space", result.getFreedSpace());
            response.put("message", result.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error during cleanup", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error during cleanup: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get device information
     */
    @GetMapping("/device/info")
    public ResponseEntity<Map<String, Object>> getDeviceInfo(
            @RequestParam(defaultValue = "0") int channel) {
        
        logger.info("Getting device info for channel: {}", channel);
        
        try {
            FingerprintDeviceService.DeviceInfo deviceInfo = deviceService.getDeviceInfo(channel);
            
            if (deviceInfo != null) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("channel_count", deviceInfo.getChannelCount());
                response.put("max_width", deviceInfo.getMaxWidth());
                response.put("max_height", deviceInfo.getMaxHeight());
                response.put("version", deviceInfo.getVersion());
                response.put("description", deviceInfo.getDescription());
                response.put("channel", deviceInfo.getChannel());
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Device not initialized or not found");
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error getting device info for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting device info: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Set device parameters
     */
    @PostMapping("/device/settings")
    public ResponseEntity<Map<String, Object>> setDeviceSettings(
            @RequestParam(defaultValue = "0") int channel,
            @RequestParam(defaultValue = "50") int brightness,
            @RequestParam(defaultValue = "50") int contrast) {
        
        logger.info("Setting device parameters for channel: {} - brightness: {}, contrast: {}", channel, brightness, contrast);
        
        try {
            boolean success = deviceService.setDeviceSettings(channel, brightness, contrast);
            
            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Device settings updated successfully");
                response.put("brightness", brightness);
                response.put("contrast", contrast);
                response.put("channel", channel);
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Failed to set device settings");
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error setting device parameters for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error setting device parameters: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Close device
     */
    @PostMapping("/close")
    public ResponseEntity<Map<String, Object>> closeDevice(
            @RequestParam(defaultValue = "0") int channel) {
        
        logger.info("Closing device for channel: {}", channel);
        
        try {
            boolean success = deviceService.closeDevice(channel);
            
            if (success) {
                Map<String, Object> response = new HashMap<>();
                response.put("success", true);
                response.put("message", "Device closed successfully");
                response.put("channel", channel);
                response.put("timestamp", new Date());
                
                return ResponseEntity.ok(response);
            } else {
                Map<String, Object> response = new HashMap<>();
                response.put("success", false);
                response.put("message", "Failed to close device");
                response.put("timestamp", new Date());
                
                return ResponseEntity.status(500).body(response);
            }
            
        } catch (Exception e) {
            logger.error("Error closing device for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error closing device: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Get device status
     */
    @GetMapping("/device/status")
    public ResponseEntity<Map<String, Object>> getDeviceStatus(
            @RequestParam(defaultValue = "0") int channel) {
        
        logger.info("Getting device status for channel: {}", channel);
        
        try {
            boolean isInitialized = deviceService.isDeviceInitialized(channel);
            Map<Integer, Boolean> allStatus = deviceService.getAllDeviceStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("channel", channel);
            response.put("is_initialized", isInitialized);
            response.put("all_channels_status", allStatus);
            response.put("timestamp", new Date());
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            logger.error("Error getting device status for channel: {}", channel, e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error getting device status: " + e.getMessage());
            response.put("timestamp", new Date());
            
            return ResponseEntity.status(500).body(response);
        }
    }
    
    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "healthy");
        response.put("timestamp", new Date());
        response.put("service", "BIO600 Fingerprint Service");
        response.put("version", "1.0.0");
        response.put("java_version", System.getProperty("java.version"));
        response.put("os_name", System.getProperty("os.name"));
        response.put("os_version", System.getProperty("os.version"));
        
        return ResponseEntity.ok(response);
    }
}

