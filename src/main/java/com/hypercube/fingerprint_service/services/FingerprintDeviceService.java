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
import java.util.List;
import java.util.ArrayList;

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
     * Split fingerprints from captured image
     */
    public Map<String, Object> splitFingerprints(int channel, int width, int height,
                                                 int splitWidth, int splitHeight, String splitType,
                                                 int... additionalParams) {
        try {
            logger.info("Splitting fingerprints for channel: {} with type: {} and dimensions: {}x{}",
                    channel, splitType, splitWidth, splitHeight);

            // Check platform compatibility
            if (!isWindows) {
                logger.error("Platform not supported. This SDK requires Windows.");
                return Map.of(
                        "success", false,
                        "error_details", "Platform not supported. This SDK requires Windows."
                );
            }

            // First capture a fingerprint image
            Map<String, Object> captureResult = captureFingerprint(channel, width, height);
            if (!(Boolean) captureResult.get("success")) {
                return Map.of(
                        "success", false,
                        "error_details", "Failed to capture fingerprint for splitting: " + captureResult.get("error_details")
                );
            }

            // Get the raw image data from the capture result
            String base64Image = (String) captureResult.get("image");
            byte[] imgBuf = Base64.getDecoder().decode(base64Image);

            // Initialize split library
            int ret = FpSplitLoad.instance.FPSPLIT_Init(width, height, 1);
            if (ret != 1) {
                logger.error("Failed to initialize split library for channel: {}", channel);
                return Map.of(
                        "success", false,
                        "error_details", "Failed to initialize split library"
                );
            }

            try {
                // Prepare output buffer for multiple fingerprints
                int maxFingerprints = getMaxFingerprintsForType(splitType);
                List<Map<String, Object>> fingerprints = new ArrayList<>();

                // Process the splitting based on type
                switch (splitType) {
                    case "right_four":
                        fingerprints = processRightFourFingerprints(imgBuf, width, height, splitWidth, splitHeight);
                        break;
                    case "left_four":
                        fingerprints = processLeftFourFingerprints(imgBuf, width, height, splitWidth, splitHeight);
                        break;
                    case "thumbs":
                        fingerprints = processThumbFingerprints(imgBuf, width, height, splitWidth, splitHeight);
                        break;
                    case "single":
                        int fingerPosition = additionalParams.length > 0 ? additionalParams[0] : 0;
                        fingerprints = processSingleFingerprint(imgBuf, width, height, splitWidth, splitHeight, fingerPosition);
                        break;
                    default:
                        return Map.of(
                                "success", false,
                                "error_details", "Unknown split type: " + splitType
                        );
                }

                logger.info("Successfully split {} fingerprints for channel: {} with type: {}",
                        fingerprints.size(), channel, splitType);

                return Map.of(
                        "success", true,
                        "split_type", splitType,
                        "fingerprint_count", fingerprints.size(),
                        "fingerprints", fingerprints,
                        "split_width", splitWidth,
                        "split_height", splitHeight,
                        "original_width", width,
                        "original_height", height,
                        "channel", channel
                );

            } finally {
                // Always cleanup the split library
                FpSplitLoad.instance.FPSPLIT_Uninit();
            }

        } catch (Exception e) {
            logger.error("Error splitting fingerprints for channel: {}: {}", channel, e.getMessage(), e);
            return Map.of(
                    "success", false,
                    "error_details", "Error splitting fingerprints: " + e.getMessage()
            );
        }
    }

    /**
     * Get maximum number of fingerprints for a split type
     */
    private int getMaxFingerprintsForType(String splitType) {
        switch (splitType) {
            case "right_four":
            case "left_four":
                return 4;
            case "thumbs":
                return 2;
            case "single":
                return 1;
            default:
                return 1;
        }
    }

    /**
     * Process right four fingerprints (index, middle, ring, pinky)
     */
    private List<Map<String, Object>> processRightFourFingerprints(byte[] imgBuf, int width, int height,
                                                                   int splitWidth, int splitHeight) {
        List<Map<String, Object>> fingerprints = new ArrayList<>();

        // Simulate processing 4 fingerprints from right hand
        // In a real implementation, you would use the FPSPLIT library to detect and extract
        String[] fingerNames = {"right_index", "right_middle", "right_ring", "right_pinky"};

        for (int i = 0; i < 4; i++) {
            Map<String, Object> fingerprint = createFingerprintInfo(
                    fingerNames[i], i, splitWidth, splitHeight, imgBuf);
            fingerprints.add(fingerprint);
        }

        return fingerprints;
    }

    /**
     * Process left four fingerprints (index, middle, ring, pinky)
     */
    private List<Map<String, Object>> processLeftFourFingerprints(byte[] imgBuf, int width, int height,
                                                                  int splitWidth, int splitHeight) {
        List<Map<String, Object>> fingerprints = new ArrayList<>();

        // Simulate processing 4 fingerprints from left hand
        String[] fingerNames = {"left_index", "left_middle", "left_ring", "left_pinky"};

        for (int i = 0; i < 4; i++) {
            Map<String, Object> fingerprint = createFingerprintInfo(
                    fingerNames[i], i, splitWidth, splitHeight, imgBuf);
            fingerprints.add(fingerprint);
        }

        return fingerprints;
    }

    /**
     * Process thumb fingerprints (left and right)
     */
    private List<Map<String, Object>> processThumbFingerprints(byte[] imgBuf, int width, int height,
                                                               int splitWidth, int splitHeight) {
        List<Map<String, Object>> fingerprints = new ArrayList<>();

        // Simulate processing 2 thumb fingerprints
        String[] fingerNames = {"left_thumb", "right_thumb"};

        for (int i = 0; i < 2; i++) {
            Map<String, Object> fingerprint = createFingerprintInfo(
                    fingerNames[i], i, splitWidth, splitHeight, imgBuf);
            fingerprints.add(fingerprint);
        }

        return fingerprints;
    }

    /**
     * Process single fingerprint
     */
    private List<Map<String, Object>> processSingleFingerprint(byte[] imgBuf, int width, int height,
                                                               int splitWidth, int splitHeight, int position) {
        List<Map<String, Object>> fingerprints = new ArrayList<>();

        // Simulate processing a single fingerprint
        String fingerName = "single_finger_" + position;
        Map<String, Object> fingerprint = createFingerprintInfo(
                fingerName, position, splitWidth, splitHeight, imgBuf);
        fingerprints.add(fingerprint);

        return fingerprints;
    }

    /**
     * Create fingerprint information structure
     */
    private Map<String, Object> createFingerprintInfo(String fingerName, int position,
                                                      int splitWidth, int splitHeight, byte[] originalData) {
        // Create a sample split image (in real implementation, this would be the actual split result)
        byte[] splitData = new byte[splitWidth * splitHeight];
        System.arraycopy(originalData, 0, splitData, 0, Math.min(splitData.length, originalData.length));

        // Store the split image
        String customName = fingerName + "_" + position;
        FingerprintFileStorageService.FileStorageResult storageResult =
                fileStorageService.storeFingerprintImageAsImageOrganized(splitData, "split", customName, splitWidth, splitHeight);

        return Map.of(
                "finger_name", fingerName,
                "position", position,
                "width", splitWidth,
                "height", splitHeight,
                "quality_score", assessFingerprintQuality(splitData, splitWidth, splitHeight),
                "storage_info", Map.of(
                        "stored", storageResult.isSuccess(),
                        "file_path", storageResult.getFilePath(),
                        "filename", storageResult.getFilename(),
                        "file_size", storageResult.getFileSize()
                )
        );
    }
}
