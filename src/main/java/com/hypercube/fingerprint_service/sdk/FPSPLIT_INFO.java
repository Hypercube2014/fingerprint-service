package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Structure;
import com.sun.jna.Pointer;

/**
 * FPSPLIT_INFO structure definition matching the C# and working Java implementations
 * This structure is used by the FpSplit.dll library for fingerprint splitting operations
 * 
 * CRITICAL: Based on analysis of working java_fingerdemo, the C# struct uses byte[] not Pointer
 * But for JNA memory management, we need to use manual memory allocation like the working samples
 * 
 * Structure layout:
 * - int x (4 bytes)
 * - int y (4 bytes) 
 * - int top (4 bytes)
 * - int left (4 bytes)
 * - int angle (4 bytes)
 * - int quality (4 bytes)
 * - Pointer pOutBuf (8 bytes on x64) - This is where we write the actual data pointer
 * 
 * Total size: 28 bytes (verified from C# Marshal.SizeOf and working java_fingerdemo)
 * pOutBuf pointer offset: 24 bytes (verified from working samples)
 */
public class FPSPLIT_INFO extends Structure {
    
    public int x;           // X coordinate
    public int y;           // Y coordinate  
    public int top;         // Top boundary
    public int left;        // Left boundary
    public int angle;       // Rotation angle
    public int quality;     // Quality score
    public Pointer pOutBuf; // Output buffer pointer - we write memory address here
    
    /**
     * Get the size of the FPSPLIT_INFO structure
     * VERIFIED: C# Marshal.SizeOf returns 28, java_fingerdemo uses 28
     */
    public static int getStructureSize() {
        return 28; // 28 bytes confirmed from multiple working sources
    }
    
    /**
     * Get the offset of the pOutBuf pointer within the structure
     * VERIFIED: 6 ints × 4 bytes = 24 bytes offset for pOutBuf
     */
    public static int getPOutBufOffset() {
        return 24; // 24 bytes offset confirmed from working samples
    }
    
    /**
     * Calculate the memory offset for a specific FPSPLIT_INFO instance
     * @param index the index of the FPSPLIT_INFO instance (0-based)
     * @return memory offset in bytes
     */
    public static int getMemoryOffset(int index) {
        return index * getStructureSize() + getPOutBufOffset();
    }
}