package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Structure;
import com.sun.jna.Pointer;
import java.util.Arrays;
import java.util.List;

/**
 * FPSPLIT_INFO structure definition matching the C# implementation
 * This structure is used by the FpSplit.dll library for fingerprint splitting operations
 * 
 * Structure layout (x64):
 * - int x (4 bytes)
 * - int y (4 bytes) 
 * - int top (4 bytes)
 * - int left (4 bytes)
 * - int angle (4 bytes)
 * - int quality (4 bytes)
 * - Pointer pOutBuf (8 bytes on x64)
 * 
 * Total size: 28 bytes on x64 architecture (matching C# Marshal.SizeOf)
 * pOutBuf pointer offset: 24 bytes
 */
public class FPSPLIT_INFO extends Structure {
    
    public int x;           // X coordinate
    public int y;           // Y coordinate  
    public int top;         // Top boundary
    public int left;        // Left boundary
    public int angle;       // Rotation angle
    public int quality;     // Quality score
    public Pointer pOutBuf; // Output buffer pointer
    
    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("x", "y", "top", "left", "angle", "quality", "pOutBuf");
    }
    
    /**
     * Get the size of the FPSPLIT_INFO structure
     * @return structure size in bytes
     */
    public static int getStructureSize() {
        FPSPLIT_INFO temp = new FPSPLIT_INFO();
        return temp.size(); // Use JNA's actual size calculation
    }
    
    /**
     * Get the offset of the pOutBuf pointer within the structure
     * @return offset in bytes
     */
    public static int getPOutBufOffset() {
        FPSPLIT_INFO temp = new FPSPLIT_INFO();
        return temp.fieldOffset("pOutBuf"); // Use JNA's actual offset calculation
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