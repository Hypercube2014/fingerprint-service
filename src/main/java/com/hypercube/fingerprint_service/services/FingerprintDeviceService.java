package com.hypercube.fingerprint_service.services;

import com.hypercube.fingerprint_service.sdk.ID_FprCapLoad;
import com.hypercube.fingerprint_service.sdk.GamcLoad;
import com.hypercube.fingerprint_service.sdk.FpSplitLoad;
import com.hypercube.fingerprint_service.sdk.FPSPLIT_INFO;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FingerprintDeviceService {
    
    private static final Logger logger = LoggerFactory.getLogger(FingerprintDeviceService.class);
    
    private final Map<Integer, Boolean> deviceStatus = new ConcurrentHashMap<>();
    private final Map<Integer, DeviceInfo> deviceInfoCache = new ConcurrentHashMap<>();
    
    @Autowired
    private FingerprintFileStorageService fileStorageService;
    
    /**
     * Initialize fingerprint device
     */
    public DeviceInitResult initializeDevice(int channel) {
        try {
            logger.info("Initializing fingerprint device for channel: {}", channel);
            
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
            if (ret == 1) {
                deviceStatus.put(channel, true);
                logger.info("Device initialized successfully for channel: {}", channel);
                
                // Cache device information
                DeviceInfo deviceInfo = getDeviceInfoInternal(channel);
                deviceInfoCache.put(channel, deviceInfo);
                
                return new DeviceInitResult(true, "Device initialized successfully", channel, deviceInfo);
            } else {
                logger.error("Device initialization failed for channel: {} with error code: {}", channel, ret);
                return new DeviceInitResult(false, "Device initialization failed", channel, null);
            }
        } catch (Exception e) {
            logger.error("Error initializing device for channel: {}", channel, e);
            return new DeviceInitResult(false, "Error initializing device: " + e.getMessage(), channel, null);
        }
    }
    
    /**
     * Capture fingerprint image with file storage
     */
    public CaptureResult captureFingerprint(int channel, int width, int height, String customName) {
        try {
            // Check if device is initialized
            if (!deviceStatus.getOrDefault(channel, false)) {
                logger.warn("Device not initialized for channel: {}, attempting to initialize", channel);
                DeviceInitResult initResult = initializeDevice(channel);
                if (!initResult.isSuccess()) {
                    return new CaptureResult(false, "Device not initialized", null, 0, 0, 0, 0, null);
                }
            }

            logger.info("Starting fingerprint capture for channel: {} with dimensions: {}x{}", channel, width, height);
            
            // Begin capture
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_BeginCapture(channel);
            if (ret != 1) {
                logger.error("Failed to begin capture for channel: {}", channel);
                return new CaptureResult(false, "Failed to begin capture", null, 0, 0, 0, 0, null);
            }

            // Get fingerprint data
            byte[] rawData = new byte[width * height];
            ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, rawData);
            
            if (ret != 1) {
                ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_EndCapture(channel);
                logger.error("Failed to capture fingerprint data for channel: {}", channel);
                return new CaptureResult(false, "Failed to capture fingerprint data", null, 0, 0, 0, 0, null);
            }

            // End capture
            ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_EndCapture(channel);

            // Assess quality
            int quality = assessFingerprintQuality(rawData, width, height);
            
            // Store file
            FingerprintFileStorageService.FileStorageResult storageResult = fileStorageService.storeFingerprintImage(
                rawData, "standard", customName
            );
            
            if (!storageResult.isSuccess()) {
                logger.warn("Failed to store fingerprint file: {}", storageResult.getMessage());
            }
            
            logger.info("Fingerprint captured successfully for channel: {} with quality: {}", channel, quality);

            return new CaptureResult(true, "Fingerprint captured successfully", rawData, width, height, quality, channel, storageResult);

        } catch (Exception e) {
            logger.error("Error capturing fingerprint for channel: {}", channel, e);
            return new CaptureResult(false, "Error capturing fingerprint: " + e.getMessage(), null, 0, 0, 0, 0, null);
        }
    }
    
    /**
     * Capture fingerprint image (overloaded for backward compatibility)
     */
    public CaptureResult captureFingerprint(int channel, int width, int height) {
        return captureFingerprint(channel, width, height, null);
    }
    
    /**
     * Capture high-resolution original image with file storage
     */
    public CaptureResult captureOriginalImage(int channel, String customName) {
        try {
            if (!deviceStatus.getOrDefault(channel, false)) {
                return new CaptureResult(false, "Device not initialized", null, 0, 0, 0, 0, null);
            }

            logger.info("Capturing original image for channel: {}", channel);
            
            int maxWidth = 2688;
            int maxHeight = 1944;
            byte[] rawData = new byte[maxWidth * maxHeight];
            
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetSrcFPRawData(channel, rawData);
            
            if (ret != 1) {
                logger.error("Failed to capture original image for channel: {}", channel);
                return new CaptureResult(false, "Failed to capture original image", null, 0, 0, 0, 0, null);
            }

            // Store file
            FingerprintFileStorageService.FileStorageResult storageResult = fileStorageService.storeFingerprintImage(
                rawData, "original", customName
            );
            
            if (!storageResult.isSuccess()) {
                logger.warn("Failed to store original image file: {}", storageResult.getMessage());
            }

            logger.info("Original image captured successfully for channel: {}", channel);
            
            return new CaptureResult(true, "Original image captured successfully", rawData, maxWidth, maxHeight, 100, channel, storageResult);

        } catch (Exception e) {
            logger.error("Error capturing original image for channel: {}", channel, e);
            return new CaptureResult(false, "Error capturing original image: " + e.getMessage(), null, 0, 0, 0, 0, null);
        }
    }
    
    /**
     * Capture high-resolution original image (overloaded for backward compatibility)
     */
    public CaptureResult captureOriginalImage(int channel) {
        return captureOriginalImage(channel, null);
    }
    
    /**
     * Capture rolled fingerprint (stitched) with file storage
     */
    public CaptureResult captureRolledFingerprint(int width, int height, String customName) {
        try {
            logger.info("Initializing mosaic library for rolled fingerprint capture");
            
            // Initialize mosaic library
            int ret = GamcLoad.instance.MOSAIC_Init();
            if (ret != 1) {
                logger.error("Failed to initialize mosaic library");
                return new CaptureResult(false, "Failed to initialize mosaic library", null, 0, 0, 0, 0, null);
            }

            // Start mosaic process
            byte[] fingerBuf = new byte[width * height];
            ret = GamcLoad.instance.MOSAIC_Start(fingerBuf, width, height);
            if (ret != 1) {
                GamcLoad.instance.MOSAIC_Close();
                logger.error("Failed to start mosaic process");
                return new CaptureResult(false, "Failed to start mosaic process", null, 0, 0, 0, 0, null);
            }

            // Perform mosaic
            ret = GamcLoad.instance.MOSAIC_DoMosaic(fingerBuf, width, height);
            if (ret != 1) {
                GamcLoad.instance.MOSAIC_Stop();
                GamcLoad.instance.MOSAIC_Close();
                logger.error("Failed to perform mosaic");
                return new CaptureResult(false, "Failed to perform mosaic", null, 0, 0, 0, 0, null);
            }

            // Stop and close
            GamcLoad.instance.MOSAIC_Stop();
            GamcLoad.instance.MOSAIC_Close();

            // Store file
            FingerprintFileStorageService.FileStorageResult storageResult = fileStorageService.storeFingerprintImage(
                fingerBuf, "rolled", customName
            );
            
            if (!storageResult.isSuccess()) {
                logger.warn("Failed to store rolled fingerprint file: {}", storageResult.getMessage());
            }

            logger.info("Rolled fingerprint captured successfully with dimensions: {}x{}", width, height);
            
            return new CaptureResult(true, "Rolled fingerprint captured successfully", fingerBuf, width, height, 90, 0, storageResult);

        } catch (Exception e) {
            logger.error("Error capturing rolled fingerprint", e);
            return new CaptureResult(false, "Error capturing rolled fingerprint: " + e.getMessage(), null, 0, 0, 0, 0, null);
        }
    }
    
    /**
     * Capture rolled fingerprint (overloaded for backward compatibility)
     */
    public CaptureResult captureRolledFingerprint(int width, int height) {
        return captureRolledFingerprint(width, height, null);
    }
    
    /**
     * Split multiple fingers from image with file storage
     */
    public SplitResult splitFingerprints(byte[] imageData, int width, int height, int splitWidth, int splitHeight, String customName) {
        try {
            logger.info("Splitting fingerprints with dimensions: {}x{} into {}x{}", width, height, splitWidth, splitHeight);
            
            // Initialize split library
            int ret = FpSplitLoad.instance.FPSPLIT_Init(width, height, 1);
            if (ret != 1) {
                logger.error("Failed to initialize split library");
                return new SplitResult(false, "Failed to initialize split library", 0, null);
            }

            // Prepare output buffer
            int size = 28; // Size of FPSPLIT_INFO structure
            Pointer infosPtr = new Memory(size * 10);
            for (int i = 0; i < 10; i++) {
                Pointer ptr = infosPtr.share(i * size + 24);
                Pointer p = new Memory(splitWidth * splitHeight);
                ptr.setPointer(0, p);
            }

            int fpNum = 0;
            ret = FpSplitLoad.instance.FPSPLIT_DoSplit(
                imageData, width, height, 1, splitWidth, splitHeight, fpNum, infosPtr
            );

            if (ret != 1) {
                FpSplitLoad.instance.FPSPLIT_Uninit();
                logger.error("Failed to split fingerprints");
                return new SplitResult(false, "Failed to split fingerprints", 0, null);
            }

            // Store split image
            FingerprintFileStorageService.FileStorageResult storageResult = fileStorageService.storeFingerprintImage(
                imageData, "split", customName
            );
            
            if (!storageResult.isSuccess()) {
                logger.warn("Failed to store split fingerprint file: {}", storageResult.getMessage());
            }

            // Process results
            // Note: This is a simplified version. In production, you'd need to properly
            // extract the FPSPLIT_INFO structures from the pointer

            FpSplitLoad.instance.FPSPLIT_Uninit();

            logger.info("Fingerprints split successfully, found {} fingerprints", fpNum);

            return new SplitResult(true, "Fingerprints split successfully", fpNum, storageResult);

        } catch (Exception e) {
            logger.error("Error splitting fingerprints", e);
            return new SplitResult(false, "Error splitting fingerprints: " + e.getMessage(), 0, null);
        }
    }
    
    /**
     * Split multiple fingers from image (overloaded for backward compatibility)
     */
    public SplitResult splitFingerprints(byte[] imageData, int width, int height, int splitWidth, int splitHeight) {
        return splitFingerprints(imageData, width, height, splitWidth, splitHeight, null);
    }
    
    /**
     * Get device information
     */
    public DeviceInfo getDeviceInfo(int channel) {
        try {
            if (!deviceStatus.getOrDefault(channel, false)) {
                return null;
            }
            
            // Return cached info if available
            if (deviceInfoCache.containsKey(channel)) {
                return deviceInfoCache.get(channel);
            }
            
            return getDeviceInfoInternal(channel);
        } catch (Exception e) {
            logger.error("Error getting device info for channel: {}", channel, e);
            return null;
        }
    }
    
    /**
     * Set device parameters
     */
    public boolean setDeviceSettings(int channel, int brightness, int contrast) {
        try {
            if (!deviceStatus.getOrDefault(channel, false)) {
                return false;
            }

            logger.info("Setting device parameters for channel: {} - brightness: {}, contrast: {}", channel, brightness, contrast);
            
            // Set brightness
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetBright(channel, brightness);
            if (ret != 1) {
                logger.error("Failed to set brightness for channel: {}", channel);
                return false;
            }

            // Set contrast
            ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetContrast(channel, contrast);
            if (ret != 1) {
                logger.error("Failed to set contrast for channel: {}", channel);
                return false;
            }

            logger.info("Device settings updated successfully for channel: {}", channel);
            return true;

        } catch (Exception e) {
            logger.error("Error setting device parameters for channel: {}", channel, e);
            return false;
        }
    }
    
    /**
     * Close device
     */
    public boolean closeDevice(int channel) {
        try {
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Close();
            deviceStatus.put(channel, false);
            deviceInfoCache.remove(channel);
            
            logger.info("Device closed successfully for channel: {}", channel);
            return true;

        } catch (Exception e) {
            logger.error("Error closing device for channel: {}", channel, e);
            return false;
        }
    }
    
    /**
     * Check if device is initialized
     */
    public boolean isDeviceInitialized(int channel) {
        return deviceStatus.getOrDefault(channel, false);
    }
    
    /**
     * Get device status for all channels
     */
    public Map<Integer, Boolean> getAllDeviceStatus() {
        return new ConcurrentHashMap<>(deviceStatus);
    }
    
    /**
     * Get storage statistics
     */
    public FingerprintFileStorageService.StorageStats getStorageStats() {
        return fileStorageService.getStorageStats();
    }
    
    /**
     * Clean up old files
     */
    public FingerprintFileStorageService.CleanupResult cleanupOldFiles(int daysToKeep) {
        return fileStorageService.cleanupOldFiles(daysToKeep);
    }
    
    /**
     * Assess fingerprint quality
     */
    private int assessFingerprintQuality(byte[] imageData, int width, int height) {
        try {
            // Use the SDK's quality assessment if available
            if (GamcLoad.instance.MOSAIC_IsSupportFingerQuality() == 1) {
                return GamcLoad.instance.MOSAIC_FingerQuality(imageData, width, height);
            }
            
            // Fallback to basic quality assessment
            return calculateBasicQuality(imageData, width, height);
            
        } catch (Exception e) {
            logger.warn("Error assessing fingerprint quality, using default score", e);
            return 75;
        }
    }
    
    /**
     * Basic quality assessment algorithm
     */
    private int calculateBasicQuality(byte[] imageData, int width, int height) {
        if (imageData == null || imageData.length == 0) {
            return 0;
        }

        // Calculate average intensity
        long totalIntensity = 0;
        for (byte b : imageData) {
            totalIntensity += (b & 0xFF);
        }
        double avgIntensity = (double) totalIntensity / imageData.length;

        // Calculate standard deviation
        double variance = 0;
        for (byte b : imageData) {
            double diff = (b & 0xFF) - avgIntensity;
            variance += diff * diff;
        }
        variance /= imageData.length;
        double stdDev = Math.sqrt(variance);

        // Quality score based on contrast and brightness
        int quality = 0;
        
        // Good contrast (high std dev) increases quality
        if (stdDev > 30) quality += 30;
        else if (stdDev > 20) quality += 20;
        else if (stdDev > 10) quality += 10;
        
        // Good brightness (not too dark, not too bright) increases quality
        if (avgIntensity > 50 && avgIntensity < 200) quality += 40;
        else if (avgIntensity > 30 && avgIntensity < 220) quality += 20;
        
        // Check for non-zero pixels (fingerprint presence)
        int nonZeroPixels = 0;
        for (byte b : imageData) {
            if ((b & 0xFF) > 0) nonZeroPixels++;
        }
        double coverage = (double) nonZeroPixels / imageData.length;
        
        if (coverage > 0.3 && coverage < 0.8) quality += 30;
        else if (coverage > 0.1 && coverage < 0.9) quality += 15;

        return Math.min(100, Math.max(0, quality));
    }
    
    /**
     * Get device information internally
     */
    private DeviceInfo getDeviceInfoInternal(int channel) {
        try {
            // Get channel count
            int channelCount = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetChannelCount();
            
            // Get max image size
            int[] width = new int[1];
            int[] height = new int[1];
            ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetMaxImageSize(channel, width, height);
            
            // Get version
            String version = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetVersion() + "";
            
            // Get description
            byte[] desc = new byte[1024];
            ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetDesc(desc);
            String description = new String(desc).trim();

            return new DeviceInfo(channelCount, width[0], height[0], version, description, channel);
        } catch (Exception e) {
            logger.error("Error getting device info internally for channel: {}", channel, e);
            return null;
        }
    }
    
    // Result classes
    public static class DeviceInitResult {
        private final boolean success;
        private final String message;
        private final int channel;
        private final DeviceInfo deviceInfo;
        
        public DeviceInitResult(boolean success, String message, int channel, DeviceInfo deviceInfo) {
            this.success = success;
            this.message = message;
            this.channel = channel;
            this.deviceInfo = deviceInfo;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getChannel() { return channel; }
        public DeviceInfo getDeviceInfo() { return deviceInfo; }
    }
    
    public static class CaptureResult {
        private final boolean success;
        private final String message;
        private final byte[] imageData;
        private final int width;
        private final int height;
        private final int quality;
        private final int channel;
        private final FingerprintFileStorageService.FileStorageResult storageResult;
        
        public CaptureResult(boolean success, String message, byte[] imageData, int width, int height, int quality, int channel, FingerprintFileStorageService.FileStorageResult storageResult) {
            this.success = success;
            this.message = message;
            this.imageData = imageData;
            this.width = width;
            this.height = height;
            this.quality = quality;
            this.channel = channel;
            this.storageResult = storageResult;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public byte[] getImageData() { return imageData; }
        public int getWidth() { return width; }
        public int getHeight() { return height; }
        public int getQuality() { return quality; }
        public int getChannel() { return channel; }
        public FingerprintFileStorageService.FileStorageResult getStorageResult() { return storageResult; }
    }
    
    public static class SplitResult {
        private final boolean success;
        private final String message;
        private final int fingerprintCount;
        private final Object fingerprints;
        
        public SplitResult(boolean success, String message, int fingerprintCount, Object fingerprints) {
            this.success = success;
            this.message = message;
            this.fingerprintCount = fingerprintCount;
            this.fingerprints = fingerprints;
        }
        
        // Getters
        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public int getFingerprintCount() { return fingerprintCount; }
        public Object getFingerprints() { return fingerprints; }
    }
    
    public static class DeviceInfo {
        private final int channelCount;
        private final int maxWidth;
        private final int maxHeight;
        private final String version;
        private final String description;
        private final int channel;
        
        public DeviceInfo(int channelCount, int maxWidth, int maxHeight, String version, String description, int channel) {
            this.channelCount = channelCount;
            this.maxWidth = maxWidth;
            this.maxHeight = maxHeight;
            this.version = version;
            this.description = description;
            this.channel = channel;
        }
        
        // Getters
        public int getChannelCount() { return channelCount; }
        public int getMaxWidth() { return maxWidth; }
        public int getMaxHeight() { return maxHeight; }
        public String getVersion() { return version; }
        public String getDescription() { return description; }
        public int getChannel() { return channel; }
    }
}



