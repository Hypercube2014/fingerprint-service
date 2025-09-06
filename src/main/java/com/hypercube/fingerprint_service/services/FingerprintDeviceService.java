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
     * Split two thumbs from a single captured image - CLEAN DEMO APP IMPLEMENTATION
     * This method follows the EXACT sequence from the demo application to properly activate hardware
     */
    public Map<String, Object> splitTwoThumbs(int channel, int width, int height, int splitWidth, int splitHeight) {
        try {
            logger.info("=== CLEAN DEMO APP IMPLEMENTATION: Split Two Thumbs ===");
            logger.info("Channel: {}, Dimensions: {}x{} -> {}x{}", channel, width, height, splitWidth, splitHeight);

            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                        "success", false,
                        "error_details", "Platform not supported. This SDK requires Windows."
                );
            }

            // STEP 1: Initialize device (EXACT demo app case 0)
            logger.info("STEP 1: Initializing device (demo app case 0)");
            int deviceRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Init();
            if (deviceRet != 1) {
                logger.error("Device initialization failed with return code: {}", deviceRet);
                return Map.of(
                        "success", false,
                        "error_details", "Device initialization failed with return code: " + deviceRet
                );
            }
            logger.info("✓ Device initialized successfully");

            // STEP 2: Initialize FPSPLIT library (EXACT demo app case 0)
            logger.info("STEP 2: Initializing FPSPLIT library with dimensions: {}x{}", width, height);
            logger.info("This should activate the green thumb indicators on your 4-4-2 scanner!");
            int fpsplitRet = FpSplitLoad.instance.FPSPLIT_Init(width, height, 1);
            logger.info("FPSPLIT_Init return value: {}", fpsplitRet);

            if (fpsplitRet != 1) {
                logger.error("FPSPLIT initialization failed with return code: {}", fpsplitRet);

                // Try with device's actual dimensions
                try {
                    int[] actualWidth = new int[1];
                    int[] actualHeight = new int[1];
                    ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetMaxImageSize(0, actualWidth, actualHeight);
                    logger.info("Trying with device's actual dimensions: {}x{}", actualWidth[0], actualHeight[0]);

                    fpsplitRet = FpSplitLoad.instance.FPSPLIT_Init(actualWidth[0], actualHeight[0], 1);
                    if (fpsplitRet == 1) {
                        width = actualWidth[0];
                        height = actualHeight[0];
                        logger.info("✓ FPSPLIT initialized with device dimensions: {}x{}", width, height);
                    } else {
                        logger.error("FPSPLIT failed even with device dimensions. Return code: {}", fpsplitRet);
                        return Map.of(
                                "success", false,
                                "error_details", "FPSPLIT initialization failed with return code: " + fpsplitRet + ". Check if ZAZ_FpStdLib.dll is present and hardware is connected."
                        );
                    }
                } catch (Exception e) {
                    logger.error("Error getting device dimensions: {}", e.getMessage());
                    return Map.of(
                            "success", false,
                            "error_details", "FPSPLIT initialization failed and cannot get device dimensions: " + e.getMessage()
                    );
                }
            } else {
                logger.info("✓ FPSPLIT library initialized successfully with dimensions: {}x{}", width, height);
            }

            // STEP 3: Play two-thumb selection sound (like demo app)
            try {
                logger.info("STEP 3: Playing two-thumb selection sound");
                int beepRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Beep(2);
                if (beepRet == 1) {
                    logger.info("✓ Two-thumb selection sound played successfully");
                } else {
                    logger.warn("Failed to play two-thumb selection sound, return code: {}", beepRet);
                }
            } catch (Exception e) {
                logger.warn("Error playing two-thumb selection sound: {}", e.getMessage());
            }

            try {
                // STEP 4: Capture fingerprint image (EXACT demo app case 2)
                logger.info("STEP 4: Capturing fingerprint image");
                logger.info("The green thumb indicators should now be active on your scanner!");

                int captureRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_BeginCapture(channel);
                if (captureRet != 1) {
                    logger.error("Failed to begin capture with return code: {}", captureRet);
                    return Map.of(
                            "success", false,
                            "error_details", "Failed to begin capture with return code: " + captureRet
                    );
                }

                // Get fingerprint data
                byte[] rawData = new byte[width * height];
                int dataRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_GetFPRawData(channel, rawData);
                if (dataRet != 1) {
                    ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_EndCapture(channel);
                    logger.error("Failed to get fingerprint data with return code: {}", dataRet);
                    return Map.of(
                            "success", false,
                            "error_details", "Failed to get fingerprint data with return code: " + dataRet
                    );
                }

                // End capture
                ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_EndCapture(channel);
                logger.info("✓ Fingerprint captured successfully");

                // STEP 5: Prepare FPSPLIT structures (EXACT demo app case 2)
                logger.info("STEP 5: Preparing FPSPLIT structures for splitting");
                int size = 28; // Size of FPSPLIT_INFO structure (from demo app)
                Pointer infosPtr = new Memory(size * 10); // Space for up to 10 fingers (like demo app)

                // Allocate memory for each finger's output buffer (exact demo app pattern)
                for (int i = 0; i < 10; i++) {
                    Pointer ptr = infosPtr.share(i * size + 24); // Exact offset from demo app
                    Pointer p = new Memory(splitWidth * splitHeight);
                    ptr.setPointer(0, p);
                }

                // STEP 6: Perform the splitting (EXACT demo app case 2)
                logger.info("STEP 6: Performing FPSPLIT_DoSplit");
                int fpNum = 0;
                int splitRet = FpSplitLoad.instance.FPSPLIT_DoSplit(
                        rawData, width, height, 1, splitWidth, splitHeight, fpNum, infosPtr
                );
                logger.info("FPSPLIT_DoSplit return value: {}", splitRet);

                if (splitRet != 1) {
                    logger.error("FPSPLIT_DoSplit failed with return code: {}", splitRet);
                    return Map.of(
                            "success", false,
                            "error_details", "FPSPLIT_DoSplit failed with return code: " + splitRet
                    );
                }

                logger.info("✓ FPSPLIT splitting completed successfully");

                // STEP 7: Process the split results and store individual thumb images
                logger.info("STEP 7: Processing split results");
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

                // STEP 8: Play success sound
                try {
                    logger.info("STEP 8: Playing success sound");
                    int successBeepRet = ID_FprCapLoad.ID_FprCapinterface.instance.LIVESCAN_Beep(1);
                    if (successBeepRet == 1) {
                        logger.info("✓ Success sound played successfully");
                    } else {
                        logger.warn("Failed to play success sound, return code: {}", successBeepRet);
                    }
                } catch (Exception e) {
                    logger.warn("Error playing success sound: {}", e.getMessage());
                }

                logger.info("✓ Successfully split {} thumbs for channel: {}", thumbs.size(), channel);

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
                successResponse.put("fpsplit_return_code", splitRet);
                successResponse.put("note", "Clean demo app implementation - FPSPLIT initialized with dimensions: " + width + "x" + height);
                successResponse.put("hardware_activation", "Green thumb indicators should have been shown during capture");

                return successResponse;

            } finally {
                // STEP 9: Cleanup FPSPLIT library (EXACT demo app case 1)
                logger.info("STEP 9: Cleaning up FPSPLIT library");
                FpSplitLoad.instance.FPSPLIT_Uninit();
                logger.info("✓ FPSPLIT library uninitialized successfully");
            }

        } catch (UnsatisfiedLinkError e) {
            logger.error("Native library cannot be loaded. Error: {}", e.getMessage());
            return Map.of(
                    "success", false,
                    "error_details", "Native library cannot be loaded. Check if all DLL files are present: GALSXXYY.dll, FpSplit.dll, ZAZ_FpStdLib.dll"
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
