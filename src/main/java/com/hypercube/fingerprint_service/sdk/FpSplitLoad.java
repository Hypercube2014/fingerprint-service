package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public interface FpSplitLoad extends Library {

    Logger logger = LoggerFactory.getLogger(FpSplitLoad.class);

    // Try to load the DLL from multiple possible locations
    FpSplitLoad instance = loadFpSplitLibrary();

    public int FPSPLIT_Init(int nImgW, int nImgH, int nPreview);
    public void FPSPLIT_Uninit();
    public int FPSPLIT_DoSplit(byte[] pImgBuf, int nImgW, int nImgH, int nPreview, int nSplitW, int nSplitH, int pnFpNum, Pointer pInfo);

    /**
     * Load the FpSplit library from the correct location
     */
    static FpSplitLoad loadFpSplitLibrary() {
        try {
            // Log system information for debugging
            logger.info("Loading FpSplit library on OS: {}, Architecture: {}, Java version: {}",
                    System.getProperty("os.name"),
                    System.getProperty("os.arch"),
                    System.getProperty("java.version"));

            // Try multiple possible locations for the DLL
            String[] possiblePaths = {
                    "FpSplit.dll",                    // Current directory
                    "./FpSplit.dll",                  // Current directory with explicit path
                    "./src/main/resources/FpSplit.dll", // Resources directory
                    "./target/classes/FpSplit.dll",   // Compiled classes directory
                    System.getProperty("user.dir") + "/FpSplit.dll", // User directory
                    System.getProperty("user.dir") + "/src/main/resources/FpSplit.dll"
            };

            for (String path : possiblePaths) {
                File dllFile = new File(path);
                if (dllFile.exists()) {
                    logger.info("Found FpSplit.dll at: {} (size: {} bytes)",
                            dllFile.getAbsolutePath(), dllFile.length());
                    try {
                        // Load the DLL using the full path with explicit options
                        Map<String, Object> options = new HashMap<>();
                        options.put(Library.OPTION_STRING_ENCODING, "UTF-8");

                        FpSplitLoad library = Native.load(dllFile.getAbsolutePath(), FpSplitLoad.class, options);
                        logger.info("Successfully loaded FpSplit.dll from: {}", path);
                        return library;
                    } catch (UnsatisfiedLinkError e) {
                        logger.warn("UnsatisfiedLinkError loading FpSplit.dll from {}: {}", path, e.getMessage());
                    } catch (Exception e) {
                        logger.warn("Failed to load FpSplit.dll from {}: {}", path, e.getMessage());
                    }
                }
            }

            // If all paths failed, try the default system PATH loading
            logger.info("Trying to load FpSplit.dll from system PATH");
            try {
                Map<String, Object> options = new HashMap<>();
                options.put(Library.OPTION_STRING_ENCODING, "UTF-8");

                FpSplitLoad library = Native.load("FpSplit", FpSplitLoad.class, options);
                logger.info("Successfully loaded FpSplit.dll from system PATH");
                return library;
            } catch (UnsatisfiedLinkError e) {
                logger.error("UnsatisfiedLinkError loading FpSplit.dll from system PATH: {}", e.getMessage());
                throw e;
            }

        } catch (Exception e) {
            logger.error("Failed to load FpSplit library: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to load FpSplit library", e);
        }
    }

    /**
     * Test if the library can be loaded and initialized
     */
    public static boolean testLibraryLoading() {
        try {
            FpSplitLoad testInstance = loadFpSplitLibrary();
            int result = testInstance.FPSPLIT_Init(1600, 1500, 1);
            if (result == 1) {
                testInstance.FPSPLIT_Uninit();
                logger.info("FpSplit library test successful");
                return true;
            } else {
                logger.error("FpSplit library test failed with return code: {}", result);
                return false;
            }
        } catch (Exception e) {
            logger.error("FpSplit library test failed with exception: {}", e.getMessage(), e);
            return false;
        }
    }
}


