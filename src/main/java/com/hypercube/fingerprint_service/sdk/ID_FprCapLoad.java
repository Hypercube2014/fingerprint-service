package com.hypercube.fingerprint_service.sdk;


import com.sun.jna.Library;
import com.sun.jna.Native;

public class ID_FprCapLoad {
    
    public interface ID_FprCapinterface extends Library {

        ID_FprCapinterface instance =Native.load("GALSXXYY", ID_FprCapinterface.class);

        public  int LIVESCAN_Init();
        public  int LIVESCAN_Close();
        //获得采集设备通道数量
        public  int LIVESCAN_GetChannelCount();
        //设置采集设备当前的亮度
        public  int LIVESCAN_SetBright(int nChannel, int nBright);
        //设置采集设备当前的对比度
        public  int LIVESCAN_SetContrast(int nChannel, int nContrast);
        //获取采集设备当前的亮度
        public  int LIVESCAN_GetBright(int nChannel, int[] nBright);
        //获取采集设备当前的对比度
        public  int LIVESCAN_GetContrast(int nChannel, int[] nContrast);
        //获得采集设备采集图像的宽度、高度的最大值
        public  int LIVESCAN_GetMaxImageSize(int nChannel, int[] pnWidth, int[] pnHeight);
        //获得当前图像的采集位置、宽度和高度
        public  int LIVESCAN_GetCaptWindow(int nChannel, int[] pnOriginX, int[] pnOriginY, int[] pnWidth, int[] pnHeight);
        //设置当前图像的采集位置、宽度和高度
        public  int LIVESCAN_SetCaptWindow(int nChannel, int pnOriginX, int pnOriginY, int pnWidth, int pnHeight);
        //调用采集仪的属性设置对话框
        public  int LIVESCAN_Setup();
        //准备采集一帧图像
        public  int LIVESCAN_BeginCapture(int nChannel);
        //采集一帧图像
        public  int LIVESCAN_GetFPRawData(int nChannel, byte[] pRawData);
        //结束采集一帧图像
        public  int LIVESCAN_EndCapture(int nChannel);
        //判断采集设备是否支持采集窗口设置
        public  int LIVESCAN_IsSupportCaptWindow(int nChannel);
        //判断采集设备是否支持设置对话框
        public  int LIVESCAN_IsSupportSetup();
        //获取预览图像大小
        public  int LIVESCAN_GetPreviewImageSize();
        //采集一帧预览图像
        public  int LIVESCAN_GetPreviewData(int nChannel, byte[] pRawData);
        //判断采集设备是否支持采集预览图像
        public  int LIVESCAN_IsSupportPreview();
        //取得接口的版本
        public  int LIVESCAN_GetVersion();
        //接口说明
        public  int LIVESCAN_GetDesc(byte[] pszDesc);
        //取得采集接口错误信息
        public  int LIVESCAN_GetErrorInfo(int nErrorNo, byte[] pszErrorInfo);
        //获取原始2688*1944大小未矫正畸变原图
        public  int LIVESCAN_GetSrcFPRawData(int nChannel, byte[] pRawData);
        //获取指定大小滚动指纹图像，一般情况传入800*750大小
        public  int LIVESCAN_GetRollFPRawData(byte[] pRawData, int width, int height);
        //获取单指图像
        public  int LIVESCAN_GetFlatFPRawData(byte[] pRawData, int width, int height);
        //畸变矫正函数，一般情况传入400*300大小
        public  int LIVESCAN_DistortionCorrection(byte[] pRawData, int width, int height,byte[] a);
        //蜂鸣控制，传入1响一声，2响两声
        public  int LIVESCAN_Beep(int beepType);
        //设置屏幕图像显示
        public  int LIVESCAN_SetLCDImage(int imageIndex);
        //设置LED显示
        public  int LIVESCAN_SetLedLight(int imageIndex);
        //获取传入图像指纹区域面积
        public  int LIVESCAN_GetFingerArea(byte[] img, int width, int height);
        //设置干湿手指
        public  int LIVESCAN_SetFingerDryWet(int nLevel);


    }

}
