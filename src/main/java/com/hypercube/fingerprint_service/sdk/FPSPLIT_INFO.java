package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;

public class FPSPLIT_INFO extends Structure {

    public int x;
    public int y;
    public int top;
    public int left;
    public int angle;
    public int quality;
    public byte[] pOutBuf;

    public FPSPLIT_INFO() {
        this.x = 0;
        this.y = 0;
        this.angle = 0;
        this.top = 0;
        this.left = 0;
        this.quality = 0;
        this.pOutBuf = new byte[300 * 400];
    }

    public FPSPLIT_INFO(int x, int y, int top, int left, int angle, int quality, byte[] pOutBuf) {
        this.x = x;
        this.y = y;
        this.top = top;
        this.left = left;
        this.angle = angle;
        this.quality = quality;
        this.pOutBuf = pOutBuf;
    }

    @Override
    protected List getFieldOrder() {
        return Arrays.asList("x","y","top","left","angle","quality","pOutBuf");
    }
}