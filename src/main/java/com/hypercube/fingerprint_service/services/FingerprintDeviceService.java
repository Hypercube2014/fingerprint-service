package com.hypercube.fingerprint_service.services;

import com.hypercube.fingerprint_service.sdk.ID_FprCapLoad;
import com.hypercube.fingerprint_service.sdk.GamcLoad;
import com.hypercube.fingerprint_service.sdk.FpSplitLoad;
import com.hypercube.fingerprint_service.sdk.FPSPLIT_INFO;
import com.hypercube.fingerprint_service.sdk.ZAZ_FpStdLib;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import com.sun.jna.ptr.IntByReference;

@Service
public class FingerprintDeviceService {
    
    private static final Logger logger = LoggerFactory.getLogger(FingerprintDeviceService.class);
    private static final Map<Integer, Boolean> deviceStatus = new ConcurrentHashMap<>();
    
    // Real-time preview management
    private static final Map<Integer, Boolean> previewStreams = new ConcurrentHashMap<>();
    private static final Map<Integer, Thread> previewThreads = new ConcurrentHashMap<>();
    private static final Map<Integer, Map<String, Object>> latestFrames = new ConcurrentHashMap<>();
    private static final Map<Integer, Boolean> previewRunning = new ConcurrentHashMap<>();
    
    private final boolean isWindows;
    private final boolean isLinux;
    
    @Autowired
    private FingerprintFileStorageService fileStorageService;
    
    public FingerprintDeviceService() {
        String os = System.getProperty("os.name").toLowerCase();
        this.isWindows = os.contains("win");
        this.isLinux = os.contains("linux") || os.contains("unix");
        
        logger.info("Detected OS: {} (Windows: {}, Linux: {})", os, isWindows, isLinux);
    }
    
    /**
     * Initialize fingerprint device for a specific channel
     */
    public boolean initializeDevice(int channel) {
        try {
            logger.info("Initializing fingerprint device for channel: {}", channel);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows. Current platform: {}", 
                    System.getProperty("os.name"));
                return false;
            }
            
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
            if (ret == 1) {
                // CORRECTED: Initialize MOSAIC library like C# sample
                try {
                    int mosaicRet = GamcLoad.instance.MOSAIC_Init();
                    if (mosaicRet == 1) {
                        logger.info("MOSAIC library initialized successfully");
                    } else {
                        logger.warn("MOSAIC library initialization failed with return code: {}", mosaicRet);
                    }
                } catch (Exception e) {
                    logger.warn("Error initializing MOSAIC library: {}", e.getMessage());
                }
                
                deviceStatus.put(channel, true);
                logger.info("Device initialized successfully for channel: {}", channel);
                return true;
            } else {
                logger.error("Device initialization failed for channel: {} with error code: {}", channel, ret);
                return false;
            }
        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. This is likely because you're running on Linux but the SDK requires Windows DLLs. Error: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error initializing device for channel: {}: {}", channel, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Capture fingerprint image
     */
    public Map<String, Object> captureFingerprint(int channel, int width, int height) {
        try {
            logger.info("Capturing fingerprint for channel: {} with dimensions: {}x{}", channel, width, height);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Begin capture
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_BeginCapture(channel);
            if (ret != 1) {
                logger.error("Failed to begin capture for channel: {} with error code: {}", channel, ret);
                return Map.of(
                    "success", false,
                    "error_details", "Failed to begin capture with error code: " + ret
                );
            }
            
            // Get fingerprint data (CORRECTED - using 2 bytes per pixel like C# sample)
            byte[] rawData = new byte[width * height * 2]; // 2 bytes per pixel for 16-bit grayscale
            ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, rawData);
            
            if (ret != 1) {
                ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_EndCapture(channel);
                logger.error("Failed to capture fingerprint data for channel: {} with error code: {}", channel, ret);
                return Map.of(
                    "success", false,
                    "error_details", "Failed to capture fingerprint data with error code: " + ret
                );
            }
            
            // End capture
            ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_EndCapture(channel);
            
            // Convert to base64
            String base64Image = Base64.getEncoder().encodeToString(rawData);
            
            // Assess quality
            int quality = assessFingerprintQuality(rawData, width, height);
            
            // Provide quality feedback based on demo implementations
            String qualityMessage = getQualityMessage(quality);
            logger.info("Fingerprint quality assessment: {} - {}", quality, qualityMessage);
            
            // Store the image automatically as normal image file
            String customName = String.format("channel_%d_%dx%d", channel, width, height);
            FingerprintFileStorageService.FileStorageResult storageResult = 
                fileStorageService.storeFingerprintImageAsImageOrganized(rawData, "standard", customName, width, height);
            
            if (storageResult.isSuccess()) {
                logger.info("Fingerprint captured and stored as image successfully for channel: {} with quality: {}. File: {}", 
                    channel, quality, storageResult.getFilePath());
            } else {
                logger.warn("Fingerprint captured but image storage failed for channel: {}. Error: {}", 
                    channel, storageResult.getMessage());
            }
            
            return Map.of(
                "success", true,
                "image", base64Image,
                "quality_score", quality,
                "quality_message", qualityMessage,
                "storage_success", storageResult.isSuccess(),
                "file_path", storageResult.getFilePath(),
                "filename", storageResult.getFilename(),
                "file_size", storageResult.getFileSize()
            );
            
        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. This is likely because you're running on Linux but the SDK requires Windows DLLs. Error: {}", e.getMessage());
            return Map.of(
                "success", false,
                "error_details", "Native library cannot be loaded. This SDK requires Windows."
            );
        } catch (Exception e) {
            logger.error("Error capturing fingerprint for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error capturing fingerprint: " + e.getMessage()
            );
        }
    }
    
    /**
     * Assess fingerprint quality using multiple methods for better accuracy
     * Prioritizes MOSAIC quality assessment as it's more reliable for this hardware
     */
    private int assessFingerprintQuality(byte[] imageData, int width, int height) {
        try {
            // Method 1: Use MOSAIC quality assessment first (most reliable for this hardware)
            try {
                if (GamcLoad.instance.MOSAIC_IsSupportFingerQuality() == 1) {
                    // Convert 16-bit data to 8-bit for MOSAIC
                    byte[] eightBitData = convert16BitTo8Bit(imageData, width, height);
                    int mosaicQuality = GamcLoad.instance.MOSAIC_FingerQuality(eightBitData, width, height);
                    logger.debug("MOSAIC quality assessment (8-bit): {}", mosaicQuality);
                    if (mosaicQuality >= 0) {
                        int quality = Math.min(100, mosaicQuality);
                        logger.debug("Using MOSAIC quality assessment: {}", quality);
                        return quality;
                    }
                }
            } catch (Exception e) {
                logger.debug("MOSAIC quality assessment (8-bit) failed: {}", e.getMessage());
            }
            
            // Method 2: Try MOSAIC quality assessment on raw 16-bit data
            try {
                if (GamcLoad.instance.MOSAIC_IsSupportFingerQuality() == 1) {
                    int quality = GamcLoad.instance.MOSAIC_FingerQuality(imageData, width, height);
                    logger.debug("MOSAIC quality assessment (raw): {}", quality);
                    // MOSAIC returns -1 for bad quality, convert to 0-100 scale
                    if (quality >= 0) {
                        int result = Math.min(100, quality);
                        logger.debug("Using MOSAIC quality assessment (raw): {}", result);
                        return result;
                    }
                }
            } catch (Exception e) {
                logger.debug("MOSAIC quality assessment (raw) failed: {}", e.getMessage());
            }
            
            // Method 3: Try ZAZ_FpStdLib quality assessment (less reliable for this hardware)
            try {
                long deviceHandle = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_OpenDevice();
                if (deviceHandle != 0) {
                    try {
                        // Convert image to standard format for ZAZ_FpStdLib
                        byte[] standardImage = convertImageToStandardFormat(imageData, width, height);
                        int quality = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_GetImageQuality(deviceHandle, standardImage);
                        logger.debug("ZAZ_FpStdLib quality assessment: {}", quality);
                        // Only use ZAZ quality if it's reasonable (not too low)
                        if (quality > 10) {
                            logger.debug("Using ZAZ_FpStdLib quality assessment: {}", quality);
                            return quality;
                        } else {
                            logger.debug("ZAZ_FpStdLib quality too low ({}), trying other methods", quality);
                        }
                    } finally {
                        ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CloseDevice(deviceHandle);
                    }
                }
            } catch (Exception e) {
                logger.debug("ZAZ_FpStdLib quality assessment failed: {}", e.getMessage());
            }
            
            // Method 4: Fallback to basic quality assessment
            int basicQuality = calculateBasicQuality(imageData, width, height);
            logger.debug("Basic quality assessment: {}", basicQuality);
            return basicQuality;
            
        } catch (Exception e) {
            logger.warn("Error assessing fingerprint quality, using default score: {}", e.getMessage());
            // Return default quality score
            return 50;
        }
    }
    
    /**
     * Basic quality assessment algorithm for 16-bit image data
     */
    private int calculateBasicQuality(byte[] imageData, int width, int height) {
        if (imageData == null || imageData.length == 0) {
            return 0;
        }

        // Convert 16-bit data to 8-bit for analysis
        byte[] eightBitData = convert16BitTo8Bit(imageData, width, height);
        
        // Calculate average intensity
        long totalIntensity = 0;
        for (byte b : eightBitData) {
            totalIntensity += (b & 0xFF);
        }
        double avgIntensity = (double) totalIntensity / eightBitData.length;

        // Calculate standard deviation
        double variance = 0;
        for (byte b : eightBitData) {
            double diff = (b & 0xFF) - avgIntensity;
            variance += diff * diff;
        }
        variance /= eightBitData.length;
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
        for (byte b : eightBitData) {
            if ((b & 0xFF) > 0) nonZeroPixels++;
        }
        double coverage = (double) nonZeroPixels / eightBitData.length;
        
        if (coverage > 0.3 && coverage < 0.8) quality += 30;
        else if (coverage > 0.1 && coverage < 0.9) quality += 15;

        return Math.min(100, Math.max(0, quality));
    }
    
    /**
     * Convert 16-bit image data to 8-bit by taking the high byte
     */
    private byte[] convert16BitTo8Bit(byte[] imageData, int width, int height) {
        try {
            int pixelCount = width * height;
            byte[] eightBitData = new byte[pixelCount];
            
            for (int i = 0; i < pixelCount; i++) {
                int srcIndex = i * 2;
                if (srcIndex + 1 < imageData.length) {
                    // Take the high byte (second byte) from 16-bit data
                    eightBitData[i] = imageData[srcIndex + 1];
                } else {
                    eightBitData[i] = 0;
                }
            }
            
            return eightBitData;
        } catch (Exception e) {
            logger.warn("Error converting 16-bit to 8-bit: {}", e.getMessage());
            return new byte[width * height];
        }
    }
    
    /**
     * Get quality message based on quality score (following demo implementations)
     */
    private String getQualityMessage(int quality) {
        if (quality < 10) {
            return "Please place finger properly on scanner";
        } else if (quality < 20) {
            return "Image captured successfully, but quality is low";
        } else if (quality < 50) {
            return "Image captured successfully, quality is acceptable";
        } else {
            return "Image quality is good, ready for template generation";
        }
    }
    
    /**
     * Check if device is initialized for a specific channel
     */
    public boolean isDeviceInitialized(int channel) {
        return deviceStatus.getOrDefault(channel, false);
    }
    
    /**
     * Get device status for all channels
     */
    public Map<Integer, Boolean> getDeviceStatus() {
        return new ConcurrentHashMap<>(deviceStatus);
    }
    
    /**
     * Close device for a specific channel
     */
    public boolean closeDevice(int channel) {
        try {
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return false;
            }
            
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Close();
            if (ret == 1) {
                deviceStatus.put(channel, false);
                logger.info("Device closed successfully for channel: {}", channel);
                return true;
            } else {
                logger.error("Failed to close device for channel: {} with error code: {}", channel, ret);
                return false;
            }
        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. This is likely because you're running on Linux but the SDK requires Windows DLLs. Error: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            logger.error("Error closing device for channel: {}: {}", channel, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get error information for a specific error code
     */
    public String getErrorInfo(int errorCode) {
        try {
            if (!isWindows) {
                return "Platform not supported. This SDK requires Windows.";
            }
            
            byte[] errorInfo = new byte[256];
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetErrorInfo(errorCode, errorInfo);
            if (ret == 1) {
                return new String(errorInfo).trim();
            } else {
                return "Unknown error code: " + errorCode;
            }
        } catch (UnsatisfiedLinkError e) {
            return "Native library cannot be loaded. This SDK requires Windows.";
        } catch (Exception e) {
            logger.error("Error getting error info for code {}: {}", errorCode, e.getMessage());
            return "Error retrieving error information";
        }
    }
    
    /**
     * Check if the current platform is supported
     */
    public boolean isPlatformSupported() {
        return isWindows;
    }
    
    /**
     * Get platform information
     */
    public String getPlatformInfo() {
        return String.format("OS: %s, Architecture: %s, Supported: %s", 
            System.getProperty("os.name"),
            System.getProperty("os.arch"),
            isPlatformSupported());
    }
    
    /**
     * Split two thumbs from a single captured image
     * This method captures an image and automatically splits it into left and right thumb images
     * Following the exact sequence from the demo application for proper hardware interaction
     */
    public Map<String, Object> splitTwoThumbs(int channel, int width, int height, int splitWidth, int splitHeight) {
        try {
            logger.info("Splitting two thumbs for channel: {} with dimensions: {}x{} -> {}x{}", 
                channel, width, height, splitWidth, splitHeight);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // CRITICAL: Follow C# sample code sequence exactly
            // Step 1: Initialize device first (like C# sample btn_open_Click)
            logger.info("Step 1: Initializing fingerprint device (following C# sample sequence)");
            int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
            if (deviceRet != 1) {
                logger.error("Device initialization failed with return code: {}", deviceRet);
                return Map.of(
                    "success", false,
                    "error_details", "Device initialization failed with return code: " + deviceRet
                );
            }
            logger.info("Device initialized successfully");
            
            // CORRECTED: Initialize MOSAIC library like C# sample
            try {
                int mosaicRet = GamcLoad.instance.MOSAIC_Init();
                if (mosaicRet == 1) {
                    logger.info("MOSAIC library initialized successfully for split operation");
                } else {
                    logger.warn("MOSAIC library initialization failed with return code: {}", mosaicRet);
                }
            } catch (Exception e) {
                logger.warn("Error initializing MOSAIC library for split operation: {}", e.getMessage());
            }
            
            // Step 2: Set capture window (like C# sample LIVESCAN_SetCaptWindow)
            logger.info("Step 2: Setting capture window to {}x{}", width, height);
            int windowRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetCaptWindow(0, 0, 0, width, height);
            if (windowRet != 1) {
                logger.warn("Failed to set capture window, return code: {}", windowRet);
            }
            
            // Step 3: Set LED/LCD display for two-thumb mode (this shows green indicators)
            logger.info("Step 3: Setting LED/LCD display for two-thumb mode (this should show green thumb indicators)");
            try {
                // Set LED display for two-thumb mode (imageIndex 4 = TWO_THUMB_FINGER)
                int ledRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetLedLight(4);
                if (ledRet == 1) {
                    logger.info("LED display set successfully for two-thumb mode");
                } else {
                    logger.warn("Failed to set LED display, return code: {}", ledRet);
                }
            } catch (Exception e) {
                logger.warn("Error setting LED display: {}", e.getMessage());
            }
            
            // Step 4: Play sound to indicate two-thumb mode is ready (like C# sample)
            try {
                logger.info("Step 4: Playing two-thumb selection sound");
                int beepRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Beep(2); // 2 beeps for two thumbs
                if (beepRet == 1) {
                    logger.info("Two-thumb selection sound played successfully");
                } else {
                    logger.warn("Failed to play two-thumb selection sound, return code: {}", beepRet);
                }
            } catch (Exception e) {
                logger.warn("Error playing two-thumb selection sound: {}", e.getMessage());
            }
            
            try {
                // Step 5: Continuous capture loop like C# sample (CORRECTED - using 2 bytes per pixel like C# sample)
                logger.info("Step 5: Starting continuous capture loop with green thumb indicators active");
                // CORRECTED: C# sample uses w * h * 2 (2 bytes per pixel for 16-bit grayscale)
                byte[] rawData = new byte[width * height * 2];
                
                long startTime = System.currentTimeMillis();
                long timeout = 10000; // 10 seconds timeout like C# sample
                int expectedFingerprints = 2; // Two thumbs
                
                int attemptCount = 0;
                while (System.currentTimeMillis() - startTime < timeout) {
                    attemptCount++;
                    long elapsed = System.currentTimeMillis() - startTime;
                    
                    logger.info("Attempt #{} (elapsed: {}ms) - Capturing fingerprint image...", attemptCount, elapsed);
                    int captureRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, rawData);
                    
                    if (captureRet != 1) {
                        logger.warn("Attempt #{} - Failed to capture fingerprint data. Return code: {}, retrying...", attemptCount, captureRet);
                        try {
                            Thread.sleep(100); // Small delay before retry
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    
                    logger.info("Attempt #{} - Image captured successfully, checking quality...", attemptCount);
                    // CORRECTED: Add quality check before split (like C# sample)
                    int quality = assessFingerprintQuality(rawData, width, height);
                    logger.info("Attempt #{} - Image quality score: {}", attemptCount, quality);
                    
                    if (quality < 10) {
                        logger.warn("Attempt #{} - Image quality too low ({}), retrying...", attemptCount, quality);
                        try {
                            Thread.sleep(100); // Small delay before retry
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    
                    logger.info("Attempt #{} - Image quality acceptable ({}), attempting split...", attemptCount, quality);
                    
                    // Step 6: Prepare output buffer for split results (CORRECTED - following C# sample exactly)
                    logger.debug("Preparing split buffers and performing FPSPLIT_DoSplit (NO FPSPLIT_Init needed)");
                    
                    // CORRECTED: Use FPSPLIT_INFO structure constants for proper memory allocation (like C# sample)
                    int size = FPSPLIT_INFO.getStructureSize(); // 32 bytes on x64
                    Pointer infosPtr = new Memory(size * 10); // Space for 10 fingerprints (like C# sample)
                    
                    // CORRECTED: Use FPSPLIT_INFO constants for correct offset calculation (like C# sample)
                    // Prepare memory for each fingerprint's output buffer (following C# sample pattern exactly)
                    for (int i = 0; i < 10; i++) { // Allocate for 10 fingerprints like C# sample
                        Pointer ptr = infosPtr.share(FPSPLIT_INFO.getMemoryOffset(i)); // Correct offset calculation
                        Pointer p = new Memory(splitWidth * splitHeight);
                        ptr.setPointer(0, p);
                    }
                    
                    // Perform the splitting (CORRECTED - using IntByReference like C# ref int)
                    logger.info("Attempt #{} - Calling FPSPLIT_DoSplit with image size: {}x{}, split size: {}x{}", attemptCount, width, height, splitWidth, splitHeight);
                    IntByReference fpNumRef = new IntByReference(0);
                    int ret = FpSplitLoad.instance.FPSPLIT_DoSplit(
                        rawData, width, height, 1, splitWidth, splitHeight, fpNumRef, infosPtr
                    );
                    
                    int fpNum = fpNumRef.getValue(); // Get the actual number of fingerprints found
                    logger.info("Attempt #{} - FPSPLIT_DoSplit returned: {}, fingerprints found: {}", attemptCount, ret, fpNum);
                    
                    if (ret != 1) {
                        logger.warn("Attempt #{} - FPSPLIT_DoSplit failed with return code: {}, retrying...", attemptCount, ret);
                        try {
                            Thread.sleep(100); // Small delay before retry
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                        continue;
                    }
                    
                    // Check if we found exactly 2 thumbs (like C# sample checks for fingernum)
                    if (fpNum == expectedFingerprints) {
                        logger.info("Attempt #{} - SUCCESS! FPSPLIT splitting completed successfully. Found exactly {} thumbs as expected. Processing results...", attemptCount, fpNum);
                        
                        // Step 7: Process the split results and store individual thumb images
                        List<Map<String, Object>> thumbs = new ArrayList<>();
                        
                        // Extract left thumb (position 0)
                        Map<String, Object> leftThumb = processSplitThumb(infosPtr, 0, "left_thumb", splitWidth, splitHeight);
                        if (leftThumb != null) {
                            thumbs.add(leftThumb);
                        }
                        
                        // Extract right thumb (position 1)
                        Map<String, Object> rightThumb = processSplitThumb(infosPtr, 1, "right_thumb", splitWidth, splitHeight);
                        if (rightThumb != null) {
                            thumbs.add(rightThumb);
                        }
                        
                        // Step 8: Play success sound and set success LED
                        try {
                            logger.info("Step 8: Playing success sound and setting success LED");
                            int successBeepRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Beep(1); // 1 beep for success
                            if (successBeepRet == 1) {
                                logger.info("Success sound played successfully");
                            } else {
                                logger.warn("Failed to play success sound, return code: {}", successBeepRet);
                            }
                            
                            // Set success LED (imageIndex 19 = TWO_THUMB_FINGER_SUCCESS)
                            int successLedRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetLedLight(19);
                            if (successLedRet == 1) {
                                logger.info("Success LED set successfully");
                            } else {
                                logger.warn("Failed to set success LED, return code: {}", successLedRet);
                            }
                        } catch (Exception e) {
                            logger.warn("Error playing success sound or setting LED: {}", e.getMessage());
                        }
                        
                        logger.info("Successfully split {} thumbs for channel: {}", thumbs.size(), channel);
                        
                        Map<String, Object> successResponse = new HashMap<>();
                        successResponse.put("success", true);
                        successResponse.put("split_type", "two_thumbs");
                        successResponse.put("thumb_count", thumbs.size());
                        successResponse.put("thumbs", thumbs);
                        successResponse.put("split_width", splitWidth);
                        successResponse.put("split_height", splitHeight);
                        successResponse.put("original_width", width);
                        successResponse.put("original_height", height);
                        successResponse.put("channel", channel);
                        successResponse.put("captured_at", new Date());
                        successResponse.put("note", "Following C# sample sequence: Device Init -> Set Window -> Set LED -> Sound -> Capture -> Split -> Success Sound/LED. NO FPSPLIT_Init required!");
                        successResponse.put("sequence_followed", "C# sample sequence: Device Init -> Set Window -> Set LED -> Sound -> Capture -> Split -> Success Sound/LED");
                        
                        return successResponse;
                    } else {
                        logger.warn("Attempt #{} - Found {} fingerprints, expected {}, retrying...", attemptCount, fpNum, expectedFingerprints);
                        try {
                            Thread.sleep(100); // Small delay before retry
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
                
                // Timeout reached
                logger.error("Timeout reached after {} attempts in {}ms while trying to split thumbs. Please ensure both thumbs are clearly visible on the scanner.", attemptCount, timeout);
                return Map.of(
                    "success", false,
                    "error_details", "Timeout reached after " + attemptCount + " attempts in " + timeout + "ms while trying to split thumbs. Please ensure both thumbs are clearly visible on the scanner.",
                    "timeout_ms", timeout,
                    "attempts_made", attemptCount,
                    "expected_fingerprints", expectedFingerprints
                );
                
            } finally {
                // No FPSPLIT cleanup needed since we never initialized it
                logger.info("No FPSPLIT cleanup needed - library was never initialized (following C# sample pattern)");
            }
            
        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. This is likely because you're running on Linux but the SDK requires Windows DLLs. Error: {}", e.getMessage());
            return Map.of(
                "success", false,
                "error_details", "Native library cannot be loaded. This SDK requires Windows."
            );
        } catch (Exception e) {
            logger.error("Error splitting two thumbs for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error splitting two thumbs: " + e.getMessage()
            );
        }
    }
    
    /**
     * Process a split thumb result and store it as an image
     */
    private Map<String, Object> processSplitThumb(Pointer infosPtr, int position, String thumbName, int splitWidth, int splitHeight) {
        try {
            // CORRECTED: Use FPSPLIT_INFO constants for correct offset calculation
            // Extract the thumb data from the pointer
            Pointer thumbPtr = infosPtr.share(FPSPLIT_INFO.getMemoryOffset(position)); // Correct offset calculation
            byte[] thumbData = thumbPtr.getByteArray(0, splitWidth * splitHeight);
            
            // Store the thumb as a PNG image
            String customName = String.format("%s_position_%d", thumbName, position);
            FingerprintFileStorageService.FileStorageResult storageResult = 
                fileStorageService.storeFingerprintImageAsImageOrganized(thumbData, "split", customName, splitWidth, splitHeight);
            
            if (storageResult.isSuccess()) {
                logger.info("Thumb {} stored successfully: {}", thumbName, storageResult.getFilePath());
                
                return Map.of(
                    "thumb_name", thumbName,
                    "position", position,
                    "width", splitWidth,
                    "height", splitHeight,
                    "quality_score", 75, // Default quality score for split images
                    "storage_info", Map.of(
                        "stored", storageResult.isSuccess(),
                        "file_path", storageResult.getFilePath(),
                        "filename", storageResult.getFilename(),
                        "file_size", storageResult.getFileSize()
                    )
                );
            } else {
                logger.warn("Failed to store thumb {}: {}", thumbName, storageResult.getMessage());
                return null;
            }
            
        } catch (Exception e) {
            logger.error("Error processing thumb {}: {}", thumbName, e.getMessage());
            return null;
        }
    }
    
    /**
     * Play sound feedback for different operations
     * @param soundType 1 = single beep (success), 2 = double beep (two-thumb mode), 3 = error beep
     */
    public boolean playSound(int soundType) {
        try {
            if (!isWindows) {
                logger.warn("Sound not supported on non-Windows platforms");
                return false;
            }
            
            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Beep(soundType);
            if (ret == 1) {
                logger.info("Sound played successfully, type: {}", soundType);
                return true;
            } else {
                logger.warn("Failed to play sound, type: {}, return code: {}", soundType, ret);
                return false;
            }
        } catch (Exception e) {
            logger.error("Error playing sound, type: {}: {}", soundType, e.getMessage());
            return false;
        }
    }
    
    /**
     * Start real-time fingerprint preview stream (following C# sample pattern)
     * This creates a continuous loop that captures and processes fingerprint images
     */
    public boolean startPreviewStream(int channel, int width, int height) {
        try {
            logger.info("Starting real-time preview stream for channel: {} with dimensions: {}x{}", channel, width, height);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return false;
            }
            
            // Stop any existing preview stream for this channel
            stopPreviewStream(channel);
            
            // Initialize device if not already initialized (following C# sample pattern)
            if (!isDeviceInitialized(channel)) {
                logger.info("Initializing device for preview stream (following C# sample pattern)");
                int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
                if (deviceRet == 1) {
                    deviceStatus.put(channel, true);
                    logger.info("Device initialized successfully for preview");
                } else {
                    logger.error("Failed to initialize device for preview stream, return code: {}", deviceRet);
                    return false;
                }
            }
            
            // Set capture window (like C# sample LIVESCAN_SetCaptWindow)
            logger.info("Setting capture window to {}x{}", width, height);
            int windowRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetCaptWindow(0, 0, 0, width, height);
            if (windowRet != 1) {
                logger.warn("Failed to set capture window, return code: {}", windowRet);
            }
            
            // Set LED display for preview mode (like C# sample)
            try {
                logger.info("Setting LED display for preview mode");
                int ledRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetLedLight(0); // 0 = normal preview
                if (ledRet == 1) {
                    logger.info("LED display set successfully for preview mode");
                } else {
                    logger.warn("Failed to set LED display, return code: {}", ledRet);
                }
            } catch (Exception e) {
                logger.warn("Error setting LED display: {}", e.getMessage());
            }
            
            // Mark preview as running
            previewRunning.put(channel, true);
            previewStreams.put(channel, true);
            
            // Start preview thread (following C# sample pattern)
            Thread previewThread = new Thread(() -> {
                logger.info("Preview thread started for channel: {} (following C# sample pattern)", channel);
                
                try {
                    byte[] data = new byte[width * height * 2]; // CORRECTED: 2 bytes per pixel like C# sample
                    long lastFrameTime = System.currentTimeMillis();
                    int frameCount = 0;
                    
                    while (previewRunning.getOrDefault(channel, false) && deviceStatus.getOrDefault(channel, false)) {
                        try {
                            // Capture fingerprint data (like C# sample LIVESCAN_GetFPRawData)
                            int ret = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, data);
                            
                            if (ret == 1) {
                                // Check quality (like C# sample MOSAIC_FingerQuality)
                                int quality = assessFingerprintQuality(data, width, height);
                                
                                // Process and store the frame (like C# sample ShowPreview)
                                Map<String, Object> frameData = processPreviewFrame(data, width, height, quality);
                                latestFrames.put(channel, frameData);
                                
                                frameCount++;
                                long currentTime = System.currentTimeMillis();
                                
                                // Log FPS every 5 seconds
                                if (currentTime - lastFrameTime >= 5000) {
                                    double fps = (frameCount * 1000.0) / (currentTime - lastFrameTime);
                                    logger.debug("Preview FPS for channel {}: {:.1f}, quality: {}", channel, fps, quality);
                                    frameCount = 0;
                                    lastFrameTime = currentTime;
                                }
                            }
                            
                            // Small delay to achieve ~15 FPS (like C# sample timing)
                            Thread.sleep(66); // ~15 FPS
                            
                        } catch (InterruptedException e) {
                            logger.info("Preview thread interrupted for channel: {}", channel);
                            break;
                        } catch (Exception e) {
                            logger.error("Error in preview loop for channel {}: {}", channel, e.getMessage());
                            Thread.sleep(100); // Wait before retrying
                        }
                    }
                    
                } catch (Exception e) {
                    logger.error("Error in preview thread for channel {}: {}", channel, e.getMessage(), e);
                } finally {
                    logger.info("Preview thread ended for channel: {}", channel);
                    previewRunning.put(channel, false);
                    previewStreams.put(channel, false);
                }
            });
            
            previewThread.setName("FingerprintPreview-" + channel);
            previewThreads.put(channel, previewThread);
            previewThread.start();
            
            logger.info("Real-time preview stream started successfully for channel: {} (15 FPS target)", channel);
            return true;
            
        } catch (Exception e) {
            logger.error("Error starting preview stream for channel {}: {}", channel, e.getMessage(), e);
            previewRunning.put(channel, false);
            previewStreams.put(channel, false);
            return false;
        }
    }
    
    /**
     * Stop real-time fingerprint preview stream
     */
    public boolean stopPreviewStream(int channel) {
        try {
            logger.info("Stopping real-time preview stream for channel: {}", channel);
            
            // Mark preview as stopped
            previewRunning.put(channel, false);
            previewStreams.put(channel, false);
            
            // Interrupt and wait for thread to finish
            Thread previewThread = previewThreads.get(channel);
            if (previewThread != null && previewThread.isAlive()) {
                previewThread.interrupt();
                try {
                    previewThread.join(2000); // Wait up to 2 seconds
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while waiting for preview thread to stop");
                }
                previewThreads.remove(channel);
            }
            
            // Clear latest frame
            latestFrames.remove(channel);
            
            logger.info("Real-time preview stream stopped successfully for channel: {}", channel);
            return true;
            
        } catch (Exception e) {
            logger.error("Error stopping preview stream for channel {}: {}", channel, e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Get current preview frame data
     */
    public Map<String, Object> getCurrentPreviewFrame(int channel) {
        try {
            Map<String, Object> frameData = latestFrames.get(channel);
            
            if (frameData != null) {
                // Add metadata
                frameData.put("channel", channel);
                frameData.put("timestamp", System.currentTimeMillis());
                frameData.put("stream_active", previewRunning.getOrDefault(channel, false));
                
                return frameData;
            } else {
                return Map.of(
                    "success", false,
                    "message", "No preview frame available",
                    "channel", channel,
                    "stream_active", previewRunning.getOrDefault(channel, false)
                );
            }
            
        } catch (Exception e) {
            logger.error("Error getting current preview frame for channel {}: {}", channel, e.getMessage());
            return Map.of(
                "success", false,
                "message", "Error getting preview frame: " + e.getMessage(),
                "channel", channel
            );
        }
    }
    
    /**
     * Process preview frame (following C# sample ShowPreview pattern)
     */
    private Map<String, Object> processPreviewFrame(byte[] rawData, int width, int height, int quality) {
        try {
            // Apply image enhancement for better preview quality
            byte[] enhancedData = enhanceFingerprintImage(rawData, width, height);
            
            // Convert enhanced data to base64
            String base64Image = Base64.getEncoder().encodeToString(enhancedData);
            
            // Create frame data similar to C# sample
            Map<String, Object> frameData = new HashMap<>();
            frameData.put("success", true);
            frameData.put("image_data", base64Image);
            frameData.put("width", width);
            frameData.put("height", height);
            frameData.put("quality", quality);
            frameData.put("format", "raw"); // Raw fingerprint data
            frameData.put("has_finger", quality > 10); // Lower threshold for better detection
            frameData.put("frame_timestamp", System.currentTimeMillis());
            
            return frameData;
            
        } catch (Exception e) {
            logger.error("Error processing preview frame: {}", e.getMessage());
            return Map.of(
                "success", false,
                "message", "Error processing frame: " + e.getMessage()
            );
        }
    }
    
    /**
     * Enhance fingerprint image for better preview quality
     */
    private byte[] enhanceFingerprintImage(byte[] rawData, int width, int height) {
        try {
            byte[] enhanced = new byte[rawData.length];
            
            // Calculate histogram for adaptive enhancement
            int[] histogram = new int[256];
            for (byte b : rawData) {
                histogram[b & 0xFF]++;
            }
            
            // Find min and max values for contrast stretching
            int min = 0, max = 255;
            for (int i = 0; i < 256; i++) {
                if (histogram[i] > 0) {
                    min = i;
                    break;
                }
            }
            for (int i = 255; i >= 0; i--) {
                if (histogram[i] > 0) {
                    max = i;
                    break;
                }
            }
            
            // Apply contrast stretching and enhancement
            double contrast = 1.5; // Increase contrast
            double brightness = 0.1; // Slight brightness adjustment
            
            for (int i = 0; i < rawData.length; i++) {
                int pixel = rawData[i] & 0xFF;
                
                // Normalize to 0-1 range
                double normalized = (pixel - min) / (double)(max - min);
                
                // Apply contrast and brightness
                normalized = Math.max(0, Math.min(1, normalized * contrast + brightness));
                
                // Convert back to byte
                enhanced[i] = (byte) Math.round(normalized * 255);
            }
            
            return enhanced;
            
        } catch (Exception e) {
            logger.warn("Error enhancing image, returning original: {}", e.getMessage());
            return rawData;
        }
    }
    
    /**
     * Check if preview stream is running for a channel
     */
    public boolean isPreviewStreamRunning(int channel) {
        return previewRunning.getOrDefault(channel, false);
    }
    
    /**
     * Test single capture and quality check (for debugging split issues)
     */
    public Map<String, Object> testCaptureAndQuality(int channel, int width, int height) {
        try {
            logger.info("Testing single capture and quality check for channel: {} with dimensions: {}x{}", channel, width, height);
            
            // Check platform compatibility
            if (!isWindows) {
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Initialize device
            int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
            if (deviceRet != 1) {
                return Map.of(
                    "success", false,
                    "error_details", "Device initialization failed with return code: " + deviceRet
                );
            }
            
            // Initialize MOSAIC library
            try {
                int mosaicRet = GamcLoad.instance.MOSAIC_Init();
                logger.info("MOSAIC library initialization result: {}", mosaicRet);
            } catch (Exception e) {
                logger.warn("Error initializing MOSAIC library: {}", e.getMessage());
            }
            
            // Set capture window
            int windowRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetCaptWindow(0, 0, 0, width, height);
            logger.info("Set capture window result: {}", windowRet);
            
            // Set LED for two-thumb mode
            int ledRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_SetLedLight(4);
            logger.info("Set LED result: {}", ledRet);
            
            // Capture image
            byte[] rawData = new byte[width * height * 2]; // 2 bytes per pixel
            int captureRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, rawData);
            logger.info("Capture result: {}", captureRet);
            
            if (captureRet != 1) {
                return Map.of(
                    "success", false,
                    "error_details", "Failed to capture image. Return code: " + captureRet
                );
            }
            
            // Check quality
            int quality = assessFingerprintQuality(rawData, width, height);
            logger.info("Image quality score: {}", quality);
            
            // Test FPSPLIT_DoSplit with minimal setup
            int size = FPSPLIT_INFO.getStructureSize();
            Pointer infosPtr = new Memory(size * 10);
            
            for (int i = 0; i < 10; i++) {
                Pointer ptr = infosPtr.share(FPSPLIT_INFO.getMemoryOffset(i));
                Pointer p = new Memory(300 * 400);
                ptr.setPointer(0, p);
            }
            
            IntByReference fpNumRef = new IntByReference(0);
            int splitRet = FpSplitLoad.instance.FPSPLIT_DoSplit(
                rawData, width, height, 1, 300, 400, fpNumRef, infosPtr
            );
            
            int fpNum = fpNumRef.getValue();
            logger.info("FPSPLIT_DoSplit result: {}, fingerprints found: {}", splitRet, fpNum);
            
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("device_init_result", deviceRet);
            result.put("mosaic_init_result", "attempted");
            result.put("capture_window_result", windowRet);
            result.put("led_result", ledRet);
            result.put("capture_result", captureRet);
            result.put("quality_score", quality);
            result.put("split_result", splitRet);
            result.put("fingerprints_found", fpNum);
            result.put("image_size_bytes", rawData.length);
            result.put("expected_image_size", width * height * 2);
            result.put("note", "Single capture test completed");
            return result;
            
        } catch (Exception e) {
            logger.error("Error in test capture and quality: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error in test: " + e.getMessage()
            );
        }
    }
    
    /**
     * Test FPSPLIT library initialization with different dimensions
     * This helps debug FPSPLIT initialization issues
     */
    public Map<String, Object> testFpSplitInitialization() {
        try {
            logger.info("Testing FPSPLIT library initialization with different dimensions...");
            
            // Check platform compatibility
            if (!isWindows) {
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Check if device is initialized first (following demo app pattern)
            boolean deviceInitialized = false;
            try {
                // Try to initialize device first (like demo app does)
                logger.info("Attempting to initialize fingerprint device first...");
                int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
                if (deviceRet == 1) {
                    deviceInitialized = true;
                    logger.info("Device initialized successfully before FPSPLIT test");
                } else {
                    logger.warn("Device initialization failed with return code: {}", deviceRet);
                }
            } catch (Exception e) {
                logger.warn("Device initialization attempt failed: {}", e.getMessage());
            }
            
            // Test different dimension combinations
            int[][] testDimensions = {
                {1600, 1500},  // Original dimensions
                {800, 600},    // Smaller dimensions
                {640, 480},    // Standard dimensions
                {400, 300},    // Small dimensions
                {320, 240}     // Very small dimensions
            };
            
            List<Map<String, Object>> testResults = new ArrayList<>();
            
            for (int[] dims : testDimensions) {
                int width = dims[0];
                int height = dims[1];
                
                try {
                    logger.info("Testing FPSPLIT_Init with dimensions: {}x{}", width, height);
                    int ret = FpSplitLoad.instance.FPSPLIT_Init(width, height, 1);
                    
                    Map<String, Object> result = Map.of(
                        "dimensions", width + "x" + height,
                        "success", ret == 1,
                        "return_code", ret,
                        "message", ret == 1 ? "Success" : 
                                  ret == 0 ? "Failed (return code 0)" : 
                                  "Failed (return code " + ret + ")"
                    );
                    
                    testResults.add(result);
                    
                    if (ret == 1) {
                        logger.info("FPSPLIT_Init SUCCESS with dimensions: {}x{}", width, height);
                        // Clean up after successful test
                        FpSplitLoad.instance.FPSPLIT_Uninit();
                    } else {
                        logger.warn("FPSPLIT_Init FAILED with dimensions: {}x{}, return code: {}", width, height, ret);
                    }
                    
                } catch (Exception e) {
                    logger.error("Error testing FPSPLIT_Init with dimensions {}x{}: {}", width, height, e.getMessage());
                    testResults.add(Map.of(
                        "dimensions", width + "x" + height,
                        "success", false,
                        "return_code", -1,
                        "message", "Exception: " + e.getMessage()
                    ));
                }
            }
            
            // Find the best working dimensions
            Map<String, Object> bestResult = testResults.stream()
                .filter(r -> (Boolean) r.get("success"))
                .findFirst()
                .orElse(null);
            
            // Clean up device if we initialized it
            if (deviceInitialized) {
                try {
                    ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Close();
                    logger.info("Device closed after FPSPLIT test");
                } catch (Exception e) {
                    logger.warn("Error closing device after test: {}", e.getMessage());
                }
            }
            
            return Map.of(
                "success", true,
                "device_initialized_for_test", deviceInitialized,
                "test_results", testResults,
                "best_working_dimensions", bestResult != null ? bestResult.get("dimensions") : "None found",
                "recommendation", bestResult != null ? 
                    "Use dimensions: " + bestResult.get("dimensions") : 
                    "Try smaller dimensions like 400x300 or 320x240. Also ensure device is initialized first.",
                "note", "FPSPLIT requires device initialization first (like demo app pattern)"
            );
            
        } catch (Exception e) {
            logger.error("Error testing FPSPLIT initialization: {}", e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error testing FPSPLIT initialization: " + e.getMessage()
            );
        }
    }
    
    /**
     * Capture fingerprint template using ZAZ_FpStdLib
     * This method captures a fingerprint image and generates templates in the specified format
     */
    public Map<String, Object> captureFingerprintTemplate(int channel, int width, int height, String format) {
        try {
            logger.info("Capturing fingerprint template for channel: {} with dimensions: {}x{}, format: {}", 
                channel, width, height, format);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Initialize ZAZ_FpStdLib device
            long deviceHandle = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_OpenDevice();
            if (deviceHandle == 0) {
                logger.error("Failed to open ZAZ_FpStdLib device");
                return Map.of(
                    "success", false,
                    "error_details", "Failed to open ZAZ_FpStdLib device"
                );
            }
            
            try {
                // Calibrate the device
                int calibRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_Calibration(deviceHandle);
                if (calibRet != 1) {
                    logger.warn("Device calibration failed with return code: {}", calibRet);
                }
                
                // Capture fingerprint image using the existing capture method
                Map<String, Object> captureResult = captureFingerprint(channel, width, height);
                if (!(Boolean) captureResult.get("success")) {
                    return Map.of(
                        "success", false,
                        "error_details", "Failed to capture fingerprint image: " + captureResult.get("error_details")
                    );
                }
                
                // Get the image data from the capture result
                String base64Image = (String) captureResult.get("image");
                byte[] imageData = Base64.getDecoder().decode(base64Image);
                
                // Convert image data to the format expected by ZAZ_FpStdLib (256x360)
                // The ZAZ_FpStdLib expects 256x360 byte array, but we have width*height*2
                // We need to resize/convert the image data
                byte[] standardImageData = convertImageToStandardFormat(imageData, width, height);
                
                // Check image quality using the improved quality assessment
                int quality = assessFingerprintQuality(imageData, width, height);
                logger.info("Fingerprint image quality: {}", quality);
                
                if (quality < 15) {
                    return Map.of(
                        "success", false,
                        "error_details", "Image quality too low: " + quality + ". Please place finger properly on scanner. Minimum quality required: 15."
                    );
                }
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("quality_score", quality);
                result.put("template_size", 1024); // Standard template size
                
                // Generate templates based on format
                if ("ISO".equals(format)) {
                    byte[] isoTemplate = new byte[1024];
                    logger.info("Creating ISO template with image size: {} bytes, template size: {} bytes", 
                        standardImageData.length, isoTemplate.length);
                    int isoRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CreateISOTemplate(deviceHandle, standardImageData, isoTemplate);
                    logger.info("ISO template creation result: {}", isoRet);
                    if (isoRet > 0) {
                        String isoTemplateBase64 = Base64.getEncoder().encodeToString(isoTemplate);
                        result.put("template_data", isoTemplateBase64);
                        logger.info("ISO template created successfully, return code: {}", isoRet);
                    } else {
                        logger.error("Failed to create ISO template, return code: {}. Image quality: {}, Image size: {} bytes", 
                            isoRet, quality, standardImageData.length);
                        return Map.of(
                            "success", false,
                            "error_details", "Failed to create ISO template, return code: " + isoRet + ". Image quality: " + quality
                        );
                    }
                } else if ("ANSI".equals(format)) {
                    byte[] ansiTemplate = new byte[1024];
                    int ansiRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CreateANSITemplate(deviceHandle, standardImageData, ansiTemplate);
                    if (ansiRet > 0) {
                        String ansiTemplateBase64 = Base64.getEncoder().encodeToString(ansiTemplate);
                        result.put("template_data", ansiTemplateBase64);
                        logger.info("ANSI template created successfully, return code: {}", ansiRet);
                    } else {
                        return Map.of(
                            "success", false,
                            "error_details", "Failed to create ANSI template, return code: " + ansiRet
                        );
                    }
                } else if ("BOTH".equals(format)) {
                    // Create both ISO and ANSI templates
                    byte[] isoTemplate = new byte[1024];
                    byte[] ansiTemplate = new byte[1024];
                    
                    int isoRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CreateISOTemplate(deviceHandle, standardImageData, isoTemplate);
                    int ansiRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CreateANSITemplate(deviceHandle, standardImageData, ansiTemplate);
                    
                    if (isoRet > 0 && ansiRet > 0) {
                        String isoTemplateBase64 = Base64.getEncoder().encodeToString(isoTemplate);
                        String ansiTemplateBase64 = Base64.getEncoder().encodeToString(ansiTemplate);
                        result.put("iso_template", Map.of(
                            "format", "ISO",
                            "template_data", isoTemplateBase64,
                            "template_size", 1024
                        ));
                        result.put("ansi_template", Map.of(
                            "format", "ANSI", 
                            "template_data", ansiTemplateBase64,
                            "template_size", 1024
                        ));
                        logger.info("Both templates created successfully - ISO: {}, ANSI: {}", isoRet, ansiRet);
                    } else {
                        return Map.of(
                            "success", false,
                            "error_details", "Failed to create templates - ISO: " + isoRet + ", ANSI: " + ansiRet
                        );
                    }
                } else {
                    return Map.of(
                        "success", false,
                        "error_details", "Invalid template format: " + format + ". Supported formats: ISO, ANSI, BOTH"
                    );
                }
                
                return result;
                
            } finally {
                // Close the ZAZ_FpStdLib device
                ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CloseDevice(deviceHandle);
            }
            
        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. This is likely because you're running on Linux but the SDK requires Windows DLLs. Error: {}", e.getMessage());
            return Map.of(
                "success", false,
                "error_details", "Native library cannot be loaded. This SDK requires Windows."
            );
        } catch (Exception e) {
            logger.error("Error capturing fingerprint template for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error capturing fingerprint template: " + e.getMessage()
            );
        }
    }
    
    /**
     * Convert image data to the standard format expected by ZAZ_FpStdLib (256x360)
     * Handles 16-bit input data (2 bytes per pixel) and converts to 8-bit output
     */
    private byte[] convertImageToStandardFormat(byte[] imageData, int width, int height) {
        try {
            // ZAZ_FpStdLib expects 256x360 = 92160 bytes (8-bit)
            int standardSize = 256 * 360;
            byte[] standardImage = new byte[standardSize];
            
            // Calculate expected input size (16-bit data: 2 bytes per pixel)
            int expectedInputSize = width * height * 2;
            
            logger.debug("Converting image: {}x{} ({} bytes) -> 256x360 ({} bytes)", 
                width, height, imageData.length, standardSize);
            
            if (imageData.length != expectedInputSize) {
                logger.warn("Unexpected image data size: {} bytes, expected: {} bytes", 
                    imageData.length, expectedInputSize);
            }
            
            // Convert 16-bit data to 8-bit by taking every other byte (high byte)
            // and resizing to 256x360
            for (int y = 0; y < 360; y++) {
                for (int x = 0; x < 256; x++) {
                    // Calculate source coordinates (scale from 256x360 to width x height)
                    int srcX = (x * width) / 256;
                    int srcY = (y * height) / 360;
                    
                    // Calculate source index in 16-bit data (2 bytes per pixel)
                    int srcIndex = (srcY * width + srcX) * 2;
                    
                    // Bounds check
                    if (srcIndex + 1 < imageData.length) {
                        // Take the high byte (second byte) from 16-bit data
                        standardImage[y * 256 + x] = imageData[srcIndex + 1];
                    } else {
                        // Pad with zero if out of bounds
                        standardImage[y * 256 + x] = 0;
                    }
                }
            }
            
            logger.debug("Image conversion completed successfully");
            return standardImage;
            
        } catch (Exception e) {
            logger.error("Error converting image to standard format: {}", e.getMessage(), e);
            // Return a default image if conversion fails
            return new byte[256 * 360];
        }
    }
    
    /**
     * Compare two fingerprint templates using ZAZ_FpStdLib
     * This method compares two templates and returns a similarity score
     */
    public Map<String, Object> compareFingerprintTemplates(String template1Base64, String template2Base64, int channel) {
        try {
            logger.info("Comparing fingerprint templates for channel: {}", channel);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Decode templates
            byte[] template1 = Base64.getDecoder().decode(template1Base64);
            byte[] template2 = Base64.getDecoder().decode(template2Base64);
            
            // Validate template sizes
            if (template1.length != 1024 || template2.length != 1024) {
                return Map.of(
                    "success", false,
                    "error_details", "Invalid template size. Expected 1024 bytes, got: " + 
                        template1.length + " and " + template2.length
                );
            }
            
            // Initialize ZAZ_FpStdLib device
            long deviceHandle = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_OpenDevice();
            if (deviceHandle == 0) {
                logger.error("Failed to open ZAZ_FpStdLib device");
                return Map.of(
                    "success", false,
                    "error_details", "Failed to open ZAZ_FpStdLib device"
                );
            }
            
            try {
                // Compare templates
                int similarityScore = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CompareTemplates(deviceHandle, template1, template2);
                
                // Determine if it's a match (threshold can be adjusted)
                int matchThreshold = 50; // Adjustable threshold
                boolean isMatch = similarityScore >= matchThreshold;
                
                logger.info("Template comparison completed - Score: {}, Threshold: {}, Match: {}", 
                    similarityScore, matchThreshold, isMatch);
                
                return Map.of(
                    "success", true,
                    "similarity_score", similarityScore,
                    "match_threshold", matchThreshold,
                    "is_match", isMatch
                );
                
            } finally {
                // Close the ZAZ_FpStdLib device
                ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CloseDevice(deviceHandle);
            }
            
        } catch (Exception e) {
            logger.error("Error comparing fingerprint templates for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error comparing fingerprint templates: " + e.getMessage()
            );
        }
    }
    
    /**
     * Validate image quality using multiple methods
     * This method helps debug quality assessment issues
     */
    public Map<String, Object> validateImageQuality(int channel, int width, int height) {
        try {
            logger.info("Validating image quality for channel: {} with dimensions: {}x{}", channel, width, height);
            
            // Check platform compatibility
            if (!isWindows) {
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Capture a test image
            byte[] rawData = new byte[width * height * 2];
            int captureRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, rawData);
            
            if (captureRet != 1) {
                return Map.of(
                    "success", false,
                    "error_details", "Failed to capture test image. Return code: " + captureRet
                );
            }
            
            // Test different quality assessment methods
            Map<String, Object> results = new HashMap<>();
            results.put("success", true);
            results.put("channel", channel);
            results.put("width", width);
            results.put("height", height);
            results.put("image_size_bytes", rawData.length);
            
            // Method 1: ZAZ_FpStdLib quality assessment
            try {
                long deviceHandle = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_OpenDevice();
                if (deviceHandle != 0) {
                    try {
                        byte[] standardImage = convertImageToStandardFormat(rawData, width, height);
                        int zazQuality = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_GetImageQuality(deviceHandle, standardImage);
                        results.put("zaz_quality", zazQuality);
                        results.put("zaz_quality_message", getQualityMessage(zazQuality));
                    } finally {
                        ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CloseDevice(deviceHandle);
                    }
                } else {
                    results.put("zaz_quality", "Failed to open device");
                }
            } catch (Exception e) {
                results.put("zaz_quality", "Error: " + e.getMessage());
            }
            
            // Method 2: MOSAIC quality assessment
            try {
                if (GamcLoad.instance.MOSAIC_IsSupportFingerQuality() == 1) {
                    int mosaicQuality = GamcLoad.instance.MOSAIC_FingerQuality(rawData, width, height);
                    results.put("mosaic_quality", mosaicQuality);
                    results.put("mosaic_quality_message", mosaicQuality < 0 ? "Bad quality" : getQualityMessage(mosaicQuality));
                } else {
                    results.put("mosaic_quality", "Not supported");
                }
            } catch (Exception e) {
                results.put("mosaic_quality", "Error: " + e.getMessage());
            }
            
            // Method 3: Basic quality assessment
            try {
                int basicQuality = calculateBasicQuality(rawData, width, height);
                results.put("basic_quality", basicQuality);
                results.put("basic_quality_message", getQualityMessage(basicQuality));
            } catch (Exception e) {
                results.put("basic_quality", "Error: " + e.getMessage());
            }
            
            // Method 4: Combined assessment (current implementation)
            try {
                int combinedQuality = assessFingerprintQuality(rawData, width, height);
                results.put("combined_quality", combinedQuality);
                results.put("combined_quality_message", getQualityMessage(combinedQuality));
            } catch (Exception e) {
                results.put("combined_quality", "Error: " + e.getMessage());
            }
            
            // Quality recommendations
            List<String> recommendations = new ArrayList<>();
            if (results.containsKey("zaz_quality") && results.get("zaz_quality") instanceof Integer) {
                int zazQuality = (Integer) results.get("zaz_quality");
                if (zazQuality < 10) {
                    recommendations.add("ZAZ quality too low - ensure finger is properly placed");
                }
            }
            if (results.containsKey("mosaic_quality") && results.get("mosaic_quality") instanceof Integer) {
                int mosaicQuality = (Integer) results.get("mosaic_quality");
                if (mosaicQuality < 0) {
                    recommendations.add("MOSAIC detected bad quality - check finger placement");
                }
            }
            
            results.put("recommendations", recommendations);
            results.put("timestamp", System.currentTimeMillis());
            
            return results;
            
        } catch (Exception e) {
            logger.error("Error validating image quality for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error validating image quality: " + e.getMessage()
            );
        }
    }
    
    /**
     * Test fingerprint template generation with relaxed quality requirements
     * This method helps debug template generation issues
     */
    public Map<String, Object> testFingerprintTemplate(int channel, int width, int height, String format) {
        try {
            logger.info("Testing fingerprint template generation for channel: {} with dimensions: {}x{}, format: {}", 
                channel, width, height, format);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Initialize ZAZ_FpStdLib device
            long deviceHandle = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_OpenDevice();
            if (deviceHandle == 0) {
                logger.error("Failed to open ZAZ_FpStdLib device");
                return Map.of(
                    "success", false,
                    "error_details", "Failed to open ZAZ_FpStdLib device"
                );
            }
            
            try {
                // Calibrate the device (like Java demo)
                int calibRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_Calibration(deviceHandle);
                logger.info("Device calibration result: {}", calibRet);
                
                // Capture fingerprint image using the existing capture method
                Map<String, Object> captureResult = captureFingerprint(channel, width, height);
                if (!(Boolean) captureResult.get("success")) {
                    return Map.of(
                        "success", false,
                        "error_details", "Failed to capture fingerprint image: " + captureResult.get("error_details")
                    );
                }
                
                // Get the image data from the capture result
                String base64Image = (String) captureResult.get("image");
                byte[] imageData = Base64.getDecoder().decode(base64Image);
                int quality = (Integer) captureResult.get("quality_score");
                
                logger.info("Captured image with quality: {} (relaxed requirements for testing)", quality);
                
                // Convert image data to the format expected by ZAZ_FpStdLib (256x360)
                byte[] standardImageData = convertImageToStandardFormat(imageData, width, height);
                
                // Debug: Check if the converted image has reasonable data
                int nonZeroPixels = 0;
                for (byte b : standardImageData) {
                    if ((b & 0xFF) > 0) nonZeroPixels++;
                }
                logger.info("Converted image: {} bytes, non-zero pixels: {}, coverage: {:.2f}%", 
                    standardImageData.length, nonZeroPixels, (nonZeroPixels * 100.0) / standardImageData.length);
                
                Map<String, Object> result = new HashMap<>();
                result.put("success", true);
                result.put("quality_score", quality);
                result.put("template_size", 1024); // Standard template size
                
                // Generate templates based on format
                if ("ISO".equals(format)) {
                    byte[] isoTemplate = new byte[1024];
                    logger.info("Creating ISO template with image size: {} bytes, template size: {} bytes", 
                        standardImageData.length, isoTemplate.length);
                    int isoRet = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CreateISOTemplate(deviceHandle, standardImageData, isoTemplate);
                    logger.info("ISO template creation result: {}", isoRet);
                    if (isoRet > 0) {
                        String isoTemplateBase64 = Base64.getEncoder().encodeToString(isoTemplate);
                        result.put("template_data", isoTemplateBase64);
                        logger.info("ISO template created successfully, return code: {}", isoRet);
                    } else {
                        logger.error("Failed to create ISO template, return code: {}. Image quality: {}, Image size: {} bytes", 
                            isoRet, quality, standardImageData.length);
                        return Map.of(
                            "success", false,
                            "error_details", "Failed to create ISO template, return code: " + isoRet + ". Image quality: " + quality
                        );
                    }
                } else {
                    return Map.of(
                        "success", false,
                        "error_details", "Invalid template format: " + format + ". Only ISO supported in test mode."
                    );
                }
                
                return result;
                
            } finally {
                // Close the ZAZ_FpStdLib device
                ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CloseDevice(deviceHandle);
            }
            
        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. This is likely because you're running on Linux but the SDK requires Windows DLLs. Error: {}", e.getMessage());
            return Map.of(
                "success", false,
                "error_details", "Native library cannot be loaded. This SDK requires Windows."
            );
        } catch (Exception e) {
            logger.error("Error testing fingerprint template for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error testing fingerprint template: " + e.getMessage()
            );
        }
    }
    
    /**
     * Search for a template in a collection of templates using ZAZ_FpStdLib
     * This method searches for a template in an array of stored templates
     */
    public Map<String, Object> searchFingerprintTemplates(String searchTemplateBase64, String templateArrayBase64, 
            int arrayCount, int matchThreshold, int channel) {
        try {
            logger.info("Searching fingerprint templates for channel: {}, array count: {}, threshold: {}", 
                channel, arrayCount, matchThreshold);
            
            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                    "success", false,
                    "error_details", "Platform not supported. This SDK requires Windows."
                );
            }
            
            // Decode templates
            byte[] searchTemplate = Base64.getDecoder().decode(searchTemplateBase64);
            byte[] templateArray = Base64.getDecoder().decode(templateArrayBase64);
            
            // Validate template sizes
            if (searchTemplate.length != 1024) {
                return Map.of(
                    "success", false,
                    "error_details", "Invalid search template size. Expected 1024 bytes, got: " + searchTemplate.length
                );
            }
            
            int expectedArraySize = arrayCount * 1024;
            if (templateArray.length != expectedArraySize) {
                return Map.of(
                    "success", false,
                    "error_details", "Invalid template array size. Expected " + expectedArraySize + 
                        " bytes, got: " + templateArray.length
                );
            }
            
            // Initialize ZAZ_FpStdLib device
            long deviceHandle = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_OpenDevice();
            if (deviceHandle == 0) {
                logger.error("Failed to open ZAZ_FpStdLib device");
                return Map.of(
                    "success", false,
                    "error_details", "Failed to open ZAZ_FpStdLib device"
                );
            }
            
            try {
                // Search templates
                int searchResult = ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_SearchingANSITemplates(
                    deviceHandle, searchTemplate, arrayCount, templateArray, matchThreshold);
                
                // Determine if a match was found
                boolean matchFound = searchResult > 0;
                int bestMatchScore = searchResult;
                
                logger.info("Template search completed - Result: {}, Threshold: {}, Match Found: {}", 
                    searchResult, matchThreshold, matchFound);
                
                return Map.of(
                    "success", true,
                    "search_result", searchResult,
                    "best_match_score", bestMatchScore,
                    "match_found", matchFound
                );
                
            } finally {
                // Close the ZAZ_FpStdLib device
                ZAZ_FpStdLib.INSTANCE.ZAZ_FpStdLib_CloseDevice(deviceHandle);
            }
            
        } catch (Exception e) {
            logger.error("Error searching fingerprint templates for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                "success", false,
                "error_details", "Error searching fingerprint templates: " + e.getMessage()
            );
        }
    }
}
