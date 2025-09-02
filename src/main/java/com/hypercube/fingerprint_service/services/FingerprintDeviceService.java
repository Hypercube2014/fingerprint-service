package com.hypercube.fingerprint_service.services;

import com.hypercube.fingerprint_service.sdk.ID_FprCapLoad;
import com.hypercube.fingerprint_service.sdk.GamcLoad;
import com.hypercube.fingerprint_service.sdk.FpSplitLoad;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class FingerprintDeviceService {
    
    private static final Logger logger = LoggerFactory.getLogger(FingerprintDeviceService.class);
    private static final Map<Integer, Boolean> deviceStatus = new ConcurrentHashMap<>();
    
    private final boolean isWindows;
    private final boolean isLinux;
    
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
}
