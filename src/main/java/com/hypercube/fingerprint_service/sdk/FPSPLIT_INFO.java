package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Structure;
import com.sun.jna.Pointer;
import java.util.Arrays;
import java.util.List;

/**
 * FPSPLIT_INFO structure definition matching the C# implementation
 * This structure is used by the FpSplit.dll library for fingerprint splitting operations
 * 
 * Structure layout (matches C# FPSPLIT_INFO exactly):
 * - int x (4 bytes)
 * - int y (4 bytes) 
 * - int top (4 bytes)
 * - int left (4 bytes)
 * - int angle (4 bytes)
 * - int quality (4 bytes)
 * - Pointer pOutBuf (8 bytes on x64, 4 bytes on x86)
 * 
 * JNA automatically handles platform-specific alignment and sizing
 */
public class FPSPLIT_INFO extends Structure {
    
    public int x;           // X coordinate
    public int y;           // Y coordinate  
    public int top;         // Top boundary
    public int left;        // Left boundary
    public int angle;       // Rotation angle
    public int quality;     // Quality score
    public Pointer pOutBuf; // Output buffer pointer
    
    public FPSPLIT_INFO() {
        super();
    }
    
    /**
     * Required by JNA - defines the order of fields in the structure
     * This MUST match the C# struct field order exactly
     */
    @Override
    protected List<String> getFieldOrder() {
        return Arrays.asList("x", "y", "top", "left", "angle", "quality", "pOutBuf");
    }
}