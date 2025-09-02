package com.hypercube.fingerprint_service.sdk;

import com.sun.jna.Library;
import com.sun.jna.Native;

public interface GamcLoad extends Library {
  
    GamcLoad instance = Native.load("Gamc", GamcLoad.class);
    //初始化拼接动态库
    public  int MOSAIC_Init();
    //释放拼接动态库
    public  int MOSAIC_Close();
    //拼接接口是否提供判断图像为指纹的函数
    public  int MOSAIC_IsSupportIdentifyFinger();
    //判断拼接接口是否提供判断图像质量的函数
    public  int MOSAIC_IsSupportImageQuality();
    //判断拼接接口是否提供判断指纹质量的函数
    public  int MOSAIC_IsSupportFingerQuality();
    //判断接口是否提供拼接指纹的图像增强功能
    public  int MOSAIC_IsSupportImageEnhance();
    //判断是否支持滚动采集
    public  int MOSAIC_IsSupportRollCap();
    //选择拼接方式
    public  int MOSAIC_SetRollMode(int nRollMode);
    //初始化拼接过程
    public  int MOSAIC_Start(byte[] pFingerBuf, int nWidth, int nHeight);
    //拼接过程
    public  int MOSAIC_DoMosaic(byte[] pFingerBuf, int nWidth, int nHeight);
    //结束拼接
    public  int MOSAIC_Stop();
    //判断图像质量
    public  int MOSAIC_ImageQuality(byte[] pFingerBuf, int nWidth, int nHeight);
    //判断指纹质量
    public  int MOSAIC_FingerQuality(byte[] pFingerBuf, int nWidth, int nHeight);
    //对图像进行增强
    public  int MOSAIC_ImageEnhance(byte[] pFingerBuf, int nWidth, int nHeight, byte[] pTargetImg);
    //判断图像是否为指纹
    public  int MOSAIC_IsFinger(byte[] pFingerBuf, int nWidth, int nHeight);
    //取得拼接接口错误信息
    public  int MOSAIC_GetErrorInfo(int nErrorNo, byte[] pszErrorInfo);
    //取得接口的版本
    public  int MOSAIC_GetVersion();
    //接口说明
    public  int MOSAIC_GetDesc(byte[] pszDesc);
}
