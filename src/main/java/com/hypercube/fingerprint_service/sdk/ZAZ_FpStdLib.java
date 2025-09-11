package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Library;
import com.sun.jna.Native;

/**
 * ZAZ_FpStdLib interface based on working Demojava implementation
 * This interface provides fingerprint template creation and comparison functionality
 */
public interface ZAZ_FpStdLib extends Library {
    
    // Load the x64 version of ZAZ_FpStdLib.dll
    ZAZ_FpStdLib INSTANCE = (ZAZ_FpStdLib) Native.loadLibrary("ZAZ_FpStdLib", ZAZ_FpStdLib.class);
    
    /**
     * Open fingerprint device
     * @return device handle (long)
     */
    long ZAZ_FpStdLib_OpenDevice();
    
    /**
     * Close fingerprint device
     * @param device device handle
     */
    void ZAZ_FpStdLib_CloseDevice(long device);
    
    /**
     * Calibrate the device
     * @param device device handle
     * @return calibration result
     */
    int ZAZ_FpStdLib_Calibration(long device);
    
    /**
     * Get fingerprint image
     * @param device device handle
     * @param image image buffer (256*360 bytes)
     * @return result code
     */
    int ZAZ_FpStdLib_GetImage(long device, byte[] image);
    
    /**
     * Get image quality
     * @param device device handle
     * @param image image buffer
     * @return quality score
     */
    int ZAZ_FpStdLib_GetImageQuality(long device, byte[] image);
    
    /**
     * Create ANSI template from image
     * @param device device handle
     * @param image image buffer
     * @param template template buffer (1024 bytes)
     * @return result code
     */
    int ZAZ_FpStdLib_CreateANSITemplate(long device, byte[] image, byte[] template);
    
    /**
     * Create ISO template from image
     * @param device device handle
     * @param image image buffer
     * @param template template buffer (1024 bytes)
     * @return result code
     */
    int ZAZ_FpStdLib_CreateISOTemplate(long device, byte[] image, byte[] template);
    
    /**
     * Compare two templates
     * @param device device handle
     * @param template1 first template
     * @param template2 second template
     * @return comparison score
     */
    int ZAZ_FpStdLib_CompareTemplates(long device, byte[] template1, byte[] template2);
    
    /**
     * Search ANSI templates
     * @param device device handle
     * @param template template to search
     * @param arrayCnt number of templates in array
     * @param templateArray array of templates to search in
     * @param matchedScoreTh score threshold
     * @return result code
     */
    int ZAZ_FpStdLib_SearchingANSITemplates(long device, byte[] template, int arrayCnt, byte[] templateArray, int matchedScoreTh);
}
