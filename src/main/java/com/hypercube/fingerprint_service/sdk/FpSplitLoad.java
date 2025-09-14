package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;
import com.sun.jna.ptr.IntByReference;

public interface  FpSplitLoad extends Library {


    FpSplitLoad instance = Native.load("FpSplit", FpSplitLoad.class);
    public  int FPSPLIT_Init(int nImgW, int nImgH, int nPreview);
    public  void FPSPLIT_Uninit();


    public int FPSPLIT_DoSplit(byte[] pImgBuf, int nImgW, int nImgH, int nPreview, int nSplitW, int nSplitH, IntByReference pnFpNum, Pointer pInfo);




}


