package com.hypercube.fingerprint_service.services;

import com.hypercube.fingerprint_service.sdk.ID_FprCapLoad;
import com.hypercube.fingerprint_service.sdk.GamcLoad;
import com.hypercube.fingerprint_service.sdk.FpSplitLoad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import com.sun.jna.Pointer;
import com.sun.jna.Memory;
import java.util.HashMap;
import java.awt.image.BufferedImage;

@Service
public class FingerprintDeviceService {

    private static final Logger logger = LoggerFactory.getLogger(FingerprintDeviceService.class);
    private static final Map<Integer, Boolean> deviceStatus = new ConcurrentHashMap<>();

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

            // Get fingerprint data
            byte[] rawData = new byte[width * height];
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
            logger.warn("Error assessing fingerprint quality, using default score: {}", e.getMessage());
            // Return default quality score
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
     * Split two thumbs from a single captured image using manual image processing
     * This method captures an image and manually splits it into left and right thumb images
     * This is a fallback solution when FPSPLIT library is not available or working
     */
    public Map<String, Object> splitTwoThumbsManual(int channel, int width, int height, int splitWidth, int splitHeight) {
        try {
            logger.info("Manual thumb splitting for channel: {} with dimensions: {}x{} -> {}x{}",
                    channel, width, height, splitWidth, splitHeight);

            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                        "success", false,
                        "error_details", "Platform not supported. This SDK requires Windows."
                );
            }

            // Ensure device is initialized
            if (!isDeviceInitialized(channel)) {
                logger.info("Device not initialized for channel {}, attempting to initialize", channel);
                boolean initSuccess = initializeDevice(channel);
                if (!initSuccess) {
                    return Map.of(
                            "success", false,
                            "error_details", "Failed to initialize device for channel: " + channel
                    );
                }
                logger.info("Device initialized successfully for channel: {}", channel);
            }

            // Capture the full image
            logger.info("Capturing full image for manual splitting...");
            Map<String, Object> captureResult = captureFingerprint(channel, width, height);
            if (!(Boolean) captureResult.get("success")) {
                return Map.of(
                        "success", false,
                        "error_details", "Failed to capture fingerprint for splitting: " + captureResult.get("error_details")
                );
            }

            // Get the raw image data
            String base64Image = (String) captureResult.get("image");
            byte[] rawData = Base64.getDecoder().decode(base64Image);

            // Convert raw data to BufferedImage
            BufferedImage fullImage = convertRawDataToImage(rawData, width, height);
            if (fullImage == null) {
                return Map.of(
                        "success", false,
                        "error_details", "Failed to convert raw data to image"
                );
            }

            // Calculate split positions (assuming two thumbs side by side)
            int leftThumbX = 0;
            int leftThumbY = 0;
            int rightThumbX = width / 2;
            int rightThumbY = 0;

            // Ensure split dimensions don't exceed image bounds
            int actualSplitWidth = Math.min(splitWidth, width / 2);
            int actualSplitHeight = Math.min(splitHeight, height);

            // Extract left thumb
            BufferedImage leftThumb = fullImage.getSubimage(leftThumbX, leftThumbY, actualSplitWidth, actualSplitHeight);

            // Extract right thumb
            BufferedImage rightThumb = fullImage.getSubimage(rightThumbX, rightThumbY, actualSplitWidth, actualSplitHeight);

            // Store the individual thumb images
            List<Map<String, Object>> thumbs = new ArrayList<>();

            // Store left thumb
            String leftThumbName = String.format("left_thumb_%dx%d", actualSplitWidth, actualSplitHeight);
            FingerprintFileStorageService.FileStorageResult leftResult =
                    fileStorageService.storeBufferedImageAsImageOrganized(leftThumb, "split", leftThumbName);

            if (leftResult.isSuccess()) {
                thumbs.add(Map.of(
                        "thumb_type", "left_thumb",
                        "position", "left",
                        "width", actualSplitWidth,
                        "height", actualSplitHeight,
                        "file_path", leftResult.getFilePath(),
                        "filename", leftResult.getFilename(),
                        "file_size", leftResult.getFileSize(),
                        "storage_success", true
                ));
                logger.info("Left thumb stored successfully: {}", leftResult.getFilePath());
            } else {
                logger.warn("Failed to store left thumb: {}", leftResult.getMessage());
            }

            // Store right thumb
            String rightThumbName = String.format("right_thumb_%dx%d", actualSplitWidth, actualSplitHeight);
            FingerprintFileStorageService.FileStorageResult rightResult =
                    fileStorageService.storeBufferedImageAsImageOrganized(rightThumb, "split", rightThumbName);

            if (rightResult.isSuccess()) {
                thumbs.add(Map.of(
                        "thumb_type", "right_thumb",
                        "position", "right",
                        "width", actualSplitWidth,
                        "height", actualSplitHeight,
                        "file_path", rightResult.getFilePath(),
                        "filename", rightResult.getFilename(),
                        "file_size", rightResult.getFileSize(),
                        "storage_success", true
                ));
                logger.info("Right thumb stored successfully: {}", rightResult.getFilePath());
            } else {
                logger.warn("Failed to store right thumb: {}", rightResult.getMessage());
            }

            logger.info("Manual thumb splitting completed. {} thumbs processed.", thumbs.size());

            Map<String, Object> successResponse = new HashMap<>();
            successResponse.put("success", true);
            successResponse.put("split_type", "manual_two_thumbs");
            successResponse.put("thumb_count", thumbs.size());
            successResponse.put("thumbs", thumbs);
            successResponse.put("split_width", actualSplitWidth);
            successResponse.put("split_height", actualSplitHeight);
            successResponse.put("original_width", width);
            successResponse.put("original_height", height);
            successResponse.put("channel", channel);
            successResponse.put("captured_at", new Date());
            successResponse.put("method", "manual_image_processing");
            successResponse.put("note", "Manual splitting used because FPSPLIT library is not working");

            return successResponse;

        } catch (Exception e) {
            logger.error("Error in manual thumb splitting for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error_details", "Error in manual thumb splitting: " + e.getMessage()
            );
        }
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

            // CRITICAL: Follow demo app sequence exactly
            // Step 1: Initialize device first (like demo app case 0)
            logger.info("Step 1: Initializing fingerprint device (following demo app sequence)");
            int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
            if (deviceRet != 1) {
                logger.error("Device initialization failed with return code: {}", deviceRet);
                return Map.of(
                        "success", false,
                        "error_details", "Device initialization failed with return code: " + deviceRet
                );
            }
            logger.info("Device initialized successfully");

            // Step 2: Initialize FPSPLIT library (this triggers green thumb indicators)
            logger.info("Step 2: Initializing FPSPLIT library with dimensions: {}x{} (this should show green thumb indicators)", width, height);
            int ret = FpSplitLoad.instance.FPSPLIT_Init(width, height, 1);
            if (ret != 1) {
                logger.error("Failed to initialize FPSPLIT library for thumb splitting. Return code: {}", ret);

                // Try with smaller dimensions if the original ones fail
                if (ret == 0) {
                    logger.info("Return code 0 detected. Trying with smaller dimensions...");
                    int[][] fallbackDimensions = {{800, 600}, {640, 480}, {400, 300}};

                    for (int[] dims : fallbackDimensions) {
                        int fallbackWidth = dims[0];
                        int fallbackHeight = dims[1];
                        logger.info("Trying FPSPLIT_Init with fallback dimensions: {}x{}", fallbackWidth, fallbackHeight);

                        ret = FpSplitLoad.instance.FPSPLIT_Init(fallbackWidth, fallbackHeight, 1);
                        if (ret == 1) {
                            logger.info("FPSPLIT_Init SUCCESS with fallback dimensions: {}x{}", fallbackWidth, fallbackHeight);
                            // Update dimensions to use the working ones
                            width = fallbackWidth;
                            height = fallbackHeight;
                            break;
                        } else {
                            logger.warn("FPSPLIT_Init FAILED with fallback dimensions {}x{}, return code: {}", fallbackWidth, fallbackHeight, ret);
                        }
                    }

                    // If still no success, return error
                    if (ret != 1) {
                        Map<String, Object> errorResponse = new HashMap<>();
                        errorResponse.put("success", false);
                        errorResponse.put("error_details", "Failed to initialize FPSPLIT library with any dimensions. Last return code: " + ret);
                        errorResponse.put("tried_dimensions", List.of("1600x1500", "800x600", "640x480", "400x300"));
                        errorResponse.put("note", "FPSPLIT library may require specific hardware or driver setup");
                        return errorResponse;
                    }
                } else {
                    return Map.of(
                            "success", false,
                            "error_details", "Failed to initialize FPSPLIT library. Return code: " + ret
                    );
                }
            }

            logger.info("FPSPLIT library initialized successfully with dimensions: {}x{}. Hardware should now show green thumb indicators.", width, height);

            // Step 3: Play sound to indicate two-thumb mode is ready (like demo app)
            try {
                logger.info("Step 3: Playing two-thumb selection sound");
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
                // Step 4: Capture the fingerprint image (after FPSPLIT is initialized and green indicators are shown)
                logger.info("Step 4: Capturing fingerprint image with green thumb indicators active");
                Map<String, Object> captureResult = captureFingerprint(channel, width, height);
                if (!(Boolean) captureResult.get("success")) {
                    return Map.of(
                            "success", false,
                            "error_details", "Failed to capture fingerprint for splitting: " + captureResult.get("error_details")
                    );
                }

                // Get the raw image data from the capture result
                String base64Image = (String) captureResult.get("image");
                byte[] rawData = Base64.getDecoder().decode(base64Image);

                // Step 5: Prepare output buffer for split results (like demo app case 2)
                logger.info("Step 5: Preparing split buffers and performing FPSPLIT_DoSplit");
                int size = 28; // Size of FPSPLIT_INFO structure
                Pointer infosPtr = new Memory(size * 2); // Space for 2 thumbs

                // Prepare memory for each thumb's output buffer (following demo app pattern)
                for (int i = 0; i < 2; i++) {
                    Pointer ptr = infosPtr.share(i * size + 24);
                    Pointer p = new Memory(splitWidth * splitHeight);
                    ptr.setPointer(0, p);
                }

                // Perform the splitting (like demo app case 2)
                int fpNum = 0;
                ret = FpSplitLoad.instance.FPSPLIT_DoSplit(
                        rawData, width, height, 1, splitWidth, splitHeight, fpNum, infosPtr
                );

                if (ret != 1) {
                    logger.error("Failed to split thumbs with FPSPLIT library. Return code: {}", ret);
                    return Map.of(
                            "success", false,
                            "error_details", "Failed to split thumbs with FPSPLIT library. Return code: " + ret
                    );
                }

                logger.info("FPSPLIT splitting completed successfully. Processing results...");

                // Step 6: Process the split results and store individual thumb images
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

                // Step 7: Play success sound
                try {
                    logger.info("Step 7: Playing success sound");
                    int successBeepRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Beep(1); // 1 beep for success
                    if (successBeepRet == 1) {
                        logger.info("Success sound played successfully");
                    } else {
                        logger.warn("Failed to play success sound, return code: {}", successBeepRet);
                    }
                } catch (Exception e) {
                    logger.warn("Error playing success sound: {}", e.getMessage());
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
                successResponse.put("note", "FPSPLIT library initialized successfully with dimensions: " + width + "x" + height + ". Green thumb indicators should have been shown during capture.");
                successResponse.put("sequence_followed", "Demo app sequence: Device Init -> FPSPLIT Init -> Sound -> Capture -> Split -> Success Sound");

                return successResponse;

            } finally {
                // Always cleanup the FPSPLIT library (like demo app case 1)
                logger.info("Cleaning up FPSPLIT library");
                FpSplitLoad.instance.FPSPLIT_Uninit();
                logger.info("FPSPLIT library uninitialized successfully");
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
            // Extract the thumb data from the pointer
            Pointer thumbPtr = infosPtr.share(position * 28 + 24);
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
     * Convert raw byte data to BufferedImage
     */
    private BufferedImage convertRawDataToImage(byte[] rawData, int width, int height) {
        try {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
            byte[] imageData = ((java.awt.image.DataBufferByte) image.getRaster().getDataBuffer()).getData();
            System.arraycopy(rawData, 0, imageData, 0, Math.min(rawData.length, imageData.length));
            return image;
        } catch (Exception e) {
            logger.error("Error converting raw data to image: {}", e.getMessage());
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
     * Deep diagnostic for FPSPLIT library issues
     * This helps identify the root cause of FPSPLIT initialization failures
     */
    public Map<String, Object> deepDiagnoseFpSplitIssues() {
        try {
            logger.info("Starting deep FPSPLIT diagnostic...");

            Map<String, Object> diagnostic = new HashMap<>();
            List<String> issues = new ArrayList<>();
            List<String> recommendations = new ArrayList<>();

            // Check platform compatibility
            if (!isWindows) {
                issues.add("Platform not supported. This SDK requires Windows.");
                recommendations.add("Run this application on a Windows system with the fingerprint device connected.");
                diagnostic.put("platform_check", Map.of(
                        "os_name", System.getProperty("os.name"),
                        "is_windows", isWindows,
                        "is_linux", isLinux,
                        "status", "FAILED"
                ));
            } else {
                diagnostic.put("platform_check", Map.of(
                        "os_name", System.getProperty("os.name"),
                        "is_windows", isWindows,
                        "is_linux", isLinux,
                        "status", "PASSED"
                ));
            }

            // Check if device can be initialized
            boolean deviceCanInit = false;
            try {
                logger.info("Testing device initialization...");
                int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
                if (deviceRet == 1) {
                    deviceCanInit = true;
                    logger.info("Device initialization successful");

                    // Get device information
                    try {
                        int channelCount = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetChannelCount();

                        int[] width = new int[1];
                        int[] height = new int[1];
                        ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetMaxImageSize(0, width, height);

                        String version = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetVersion() + "";

                        byte[] desc = new byte[1024];
                        ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetDesc(desc);
                        String description = new String(desc).trim();

                        diagnostic.put("device_info", Map.of(
                                "channel_count", channelCount,
                                "max_width", width[0],
                                "max_height", height[0],
                                "version", version,
                                "description", description,
                                "status", "CONNECTED"
                        ));

                        // Test FPSPLIT with device's maximum dimensions
                        logger.info("Testing FPSPLIT with device's maximum dimensions: {}x{}", width[0], height[0]);
                        int fpsplitRet = FpSplitLoad.instance.FPSPLIT_Init(width[0], height[0], 1);

                        diagnostic.put("fpsplit_test_max_dims", Map.of(
                                "dimensions", width[0] + "x" + height[0],
                                "return_code", fpsplitRet,
                                "success", fpsplitRet == 1,
                                "status", fpsplitRet == 1 ? "SUCCESS" : "FAILED"
                        ));

                        if (fpsplitRet == 1) {
                            FpSplitLoad.instance.FPSPLIT_Uninit();
                            logger.info("FPSPLIT works with device's maximum dimensions!");
                        } else {
                            issues.add("FPSPLIT failed with device's maximum dimensions (return code: " + fpsplitRet + ")");

                            // Try smaller dimensions
                            int[][] testDims = {{800, 600}, {640, 480}, {400, 300}, {320, 240}};
                            for (int[] dims : testDims) {
                                int testRet = FpSplitLoad.instance.FPSPLIT_Init(dims[0], dims[1], 1);
                                if (testRet == 1) {
                                    logger.info("FPSPLIT works with dimensions: {}x{}", dims[0], dims[1]);
                                    diagnostic.put("fpsplit_working_dims", dims[0] + "x" + dims[1]);
                                    break;
                                }
                            }
                        }

                    } catch (Exception e) {
                        issues.add("Error getting device information: " + e.getMessage());
                        logger.error("Error getting device info: {}", e.getMessage());
                    }

                    // Clean up device
                    ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Close();

                } else {
                    issues.add("Device initialization failed with return code: " + deviceRet);
                    recommendations.add("Check if fingerprint device is connected and drivers are installed");
                    diagnostic.put("device_info", Map.of(
                            "return_code", deviceRet,
                            "status", "FAILED"
                    ));
                }
            } catch (Exception e) {
                issues.add("Device initialization exception: " + e.getMessage());
                recommendations.add("Check if fingerprint device drivers are properly installed");
                diagnostic.put("device_info", Map.of(
                        "exception", e.getMessage(),
                        "status", "ERROR"
                ));
            }

            // Check DLL file availability
            try {
                // Try to load the FPSPLIT library directly
                FpSplitLoad testLoad = FpSplitLoad.instance;
                diagnostic.put("dll_loading", Map.of(
                        "fpsplit_dll", "LOADED",
                        "status", "SUCCESS"
                ));
            } catch (Exception e) {
                issues.add("FPSPLIT DLL loading failed: " + e.getMessage());
                recommendations.add("Ensure FpSplit.dll is in the classpath and accessible");
                diagnostic.put("dll_loading", Map.of(
                        "exception", e.getMessage(),
                        "status", "FAILED"
                ));
            }

            // Generate recommendations
            if (issues.isEmpty()) {
                recommendations.add("All checks passed. FPSPLIT should work correctly.");
            } else {
                if (issues.stream().anyMatch(issue -> issue.contains("Platform not supported"))) {
                    recommendations.add("Run on Windows system");
                }
                if (issues.stream().anyMatch(issue -> issue.contains("Device initialization"))) {
                    recommendations.add("Connect fingerprint device and install drivers");
                }
                if (issues.stream().anyMatch(issue -> issue.contains("DLL loading"))) {
                    recommendations.add("Check DLL file locations and permissions");
                }
                if (issues.stream().anyMatch(issue -> issue.contains("FPSPLIT failed"))) {
                    recommendations.add("Try different dimensions or check hardware compatibility");
                }
            }

            diagnostic.put("issues_found", issues);
            diagnostic.put("recommendations", recommendations);
            diagnostic.put("overall_status", issues.isEmpty() ? "HEALTHY" : "ISSUES_DETECTED");

            return Map.of(
                    "success", true,
                    "diagnostic", diagnostic,
                    "summary", issues.isEmpty() ?
                            "All systems operational. FPSPLIT should work correctly." :
                            "Issues detected: " + String.join(", ", issues)
            );

        } catch (Exception e) {
            logger.error("Error during FPSPLIT diagnostic: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error_details", "Error during diagnostic: " + e.getMessage()
            );
        }
    }

    /**
     * Comprehensive diagnostic for FPSPLIT library issues
     * This helps identify the root cause of FPSPLIT initialization failures
     */
    public Map<String, Object> diagnoseFpSplitIssues() {
        try {
            logger.info("Starting comprehensive FPSPLIT diagnostic...");

            Map<String, Object> diagnostic = new HashMap<>();
            List<String> issues = new ArrayList<>();
            List<String> recommendations = new ArrayList<>();

            // Check platform compatibility
            if (!isWindows) {
                issues.add("Platform not supported. This SDK requires Windows.");
                recommendations.add("Run this application on a Windows system with the fingerprint device connected.");
                diagnostic.put("platform_check", Map.of(
                        "os_name", System.getProperty("os.name"),
                        "is_windows", isWindows,
                        "is_linux", isLinux,
                        "status", "FAILED"
                ));
            } else {
                diagnostic.put("platform_check", Map.of(
                        "os_name", System.getProperty("os.name"),
                        "is_windows", isWindows,
                        "is_linux", isLinux,
                        "status", "PASSED"
                ));
            }

            // Check if device can be initialized
            boolean deviceCanInit = false;
            try {
                logger.info("Testing device initialization...");
                int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
                if (deviceRet == 1) {
                    deviceCanInit = true;
                    logger.info("Device initialization successful");

                    // Get device information
                    try {
                        int channelCount = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetChannelCount();

                        int[] width = new int[1];
                        int[] height = new int[1];
                        ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetMaxImageSize(0, width, height);

                        String version = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetVersion() + "";

                        byte[] desc = new byte[1024];
                        ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetDesc(desc);
                        String description = new String(desc).trim();

                        diagnostic.put("device_info", Map.of(
                                "channel_count", channelCount,
                                "max_width", width[0],
                                "max_height", height[0],
                                "version", version,
                                "description", description,
                                "status", "CONNECTED"
                        ));

                        // Test FPSPLIT with device's maximum dimensions
                        logger.info("Testing FPSPLIT with device's maximum dimensions: {}x{}", width[0], height[0]);
                        int fpsplitRet = FpSplitLoad.instance.FPSPLIT_Init(width[0], height[0], 1);

                        diagnostic.put("fpsplit_test_max_dims", Map.of(
                                "dimensions", width[0] + "x" + height[0],
                                "return_code", fpsplitRet,
                                "success", fpsplitRet == 1,
                                "status", fpsplitRet == 1 ? "SUCCESS" : "FAILED"
                        ));

                        if (fpsplitRet == 1) {
                            FpSplitLoad.instance.FPSPLIT_Uninit();
                            logger.info("FPSPLIT works with device's maximum dimensions!");
                        } else {
                            issues.add("FPSPLIT failed with device's maximum dimensions (return code: " + fpsplitRet + ")");

                            // Try smaller dimensions
                            int[][] testDims = {{800, 600}, {640, 480}, {400, 300}, {320, 240}};
                            for (int[] dims : testDims) {
                                int testRet = FpSplitLoad.instance.FPSPLIT_Init(dims[0], dims[1], 1);
                                if (testRet == 1) {
                                    logger.info("FPSPLIT works with dimensions: {}x{}", dims[0], dims[1]);
                                    diagnostic.put("fpsplit_working_dims", dims[0] + "x" + dims[1]);
                                    break;
                                }
                            }
                        }

                    } catch (Exception e) {
                        issues.add("Error getting device information: " + e.getMessage());
                        logger.error("Error getting device info: {}", e.getMessage());
                    }

                    // Clean up device
                    ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Close();

                } else {
                    issues.add("Device initialization failed with return code: " + deviceRet);
                    recommendations.add("Check if fingerprint device is connected and drivers are installed");
                    diagnostic.put("device_info", Map.of(
                            "return_code", deviceRet,
                            "status", "FAILED"
                    ));
                }
            } catch (Exception e) {
                issues.add("Device initialization exception: " + e.getMessage());
                recommendations.add("Check if fingerprint device drivers are properly installed");
                diagnostic.put("device_info", Map.of(
                        "exception", e.getMessage(),
                        "status", "ERROR"
                ));
            }

            // Check DLL file availability
            try {
                // Try to load the FPSPLIT library directly
                FpSplitLoad testLoad = FpSplitLoad.instance;
                diagnostic.put("dll_loading", Map.of(
                        "fpsplit_dll", "LOADED",
                        "status", "SUCCESS"
                ));
            } catch (Exception e) {
                issues.add("FPSPLIT DLL loading failed: " + e.getMessage());
                recommendations.add("Ensure FpSplit.dll is in the classpath and accessible");
                diagnostic.put("dll_loading", Map.of(
                        "exception", e.getMessage(),
                        "status", "FAILED"
                ));
            }

            // Generate recommendations
            if (issues.isEmpty()) {
                recommendations.add("All checks passed. FPSPLIT should work correctly.");
            } else {
                if (issues.stream().anyMatch(issue -> issue.contains("Platform not supported"))) {
                    recommendations.add("Run on Windows system");
                }
                if (issues.stream().anyMatch(issue -> issue.contains("Device initialization"))) {
                    recommendations.add("Connect fingerprint device and install drivers");
                }
                if (issues.stream().anyMatch(issue -> issue.contains("DLL loading"))) {
                    recommendations.add("Check DLL file locations and permissions");
                }
                if (issues.stream().anyMatch(issue -> issue.contains("FPSPLIT failed"))) {
                    recommendations.add("Try different dimensions or check hardware compatibility");
                }
            }

            diagnostic.put("issues_found", issues);
            diagnostic.put("recommendations", recommendations);
            diagnostic.put("overall_status", issues.isEmpty() ? "HEALTHY" : "ISSUES_DETECTED");

            return Map.of(
                    "success", true,
                    "diagnostic", diagnostic,
                    "summary", issues.isEmpty() ?
                            "All systems operational. FPSPLIT should work correctly." :
                            "Issues detected: " + String.join(", ", issues)
            );

        } catch (Exception e) {
            logger.error("Error during FPSPLIT diagnostic: {}", e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error_details", "Error during diagnostic: " + e.getMessage()
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
}
