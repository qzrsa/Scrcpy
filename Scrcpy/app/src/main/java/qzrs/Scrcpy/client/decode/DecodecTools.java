package qzrs.Scrcpy.client.decode;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.util.ArrayList;
import java.util.Objects;

public class DecodecTools {
  private static ArrayList<String> hevcDecodecList = null;
  private static ArrayList<String> avcDecodecList = null;
  private static ArrayList<String> vp8DecodecList = null;
  private static ArrayList<String> vp9DecodecList = null;
  private static ArrayList<String> opusDecodecList = null;
  private static Boolean isSupportOpus = null;
  private static Boolean isSupportH265 = null;
  private static Boolean isSupportVp8 = null;
  private static Boolean isSupportVp9 = null;

  // 获取解码器列表
  private static void getDecodecList() {
    MediaCodecList mediaCodecList = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    hevcDecodecList = new ArrayList<>();
    avcDecodecList = new ArrayList<>();
    vp8DecodecList = new ArrayList<>();
    vp9DecodecList = new ArrayList<>();
    opusDecodecList = new ArrayList<>();
    for (MediaCodecInfo mediaCodecInfo : mediaCodecList.getCodecInfos()) {
      if (!mediaCodecInfo.isEncoder()) {
        String codecName = mediaCodecInfo.getName();
        for (String supportType : mediaCodecInfo.getSupportedTypes()) {
          if (Objects.equals(supportType, MediaFormat.MIMETYPE_AUDIO_OPUS)) opusDecodecList.add(codecName);
          else {
            // 视频解码器要求硬件实现
            if (!codecName.startsWith("OMX.google") && !codecName.startsWith("c2.android")) {
              if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_HEVC)) hevcDecodecList.add(codecName);
              else if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_AVC)) avcDecodecList.add(codecName);
              else if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_VP8)) vp8DecodecList.add(codecName);
              else if (Objects.equals(supportType, MediaFormat.MIMETYPE_VIDEO_VP9)) vp9DecodecList.add(codecName);
            }
          }
        }
      }
    }
  }

  // 获取解码器是否支持
  public static boolean isSupportOpus() {
    if (isSupportOpus != null) return isSupportOpus;
    if (opusDecodecList == null) getDecodecList();
    isSupportOpus = opusDecodecList.size() > 0;
    return isSupportOpus;
  }

  public static boolean isSupportH265() {
    if (isSupportH265 != null) return isSupportH265;
    if (hevcDecodecList == null) getDecodecList();
    isSupportH265 = hevcDecodecList.size() > 0;
    return isSupportH265;
  }

  public static boolean isSupportVp8() {
    if (isSupportVp8 != null) return isSupportVp8;
    if (vp8DecodecList == null) getDecodecList();
    isSupportVp8 = vp8DecodecList.size() > 0;
    return isSupportVp8;
  }

  public static boolean isSupportVp9() {
    if (isSupportVp9 != null) return isSupportVp9;
    if (vp9DecodecList == null) getDecodecList();
    isSupportVp9 = vp9DecodecList.size() > 0;
    return isSupportVp9;
  }

  // 获取视频最优解码器
  public static String getVideoDecoder(boolean h265) {
    return getVideoDecoder(h265 ? MediaFormat.MIMETYPE_VIDEO_HEVC : MediaFormat.MIMETYPE_VIDEO_AVC);
  }

  public static String getVideoDecoder(String mime) {
    if (hevcDecodecList == null || avcDecodecList == null || vp8DecodecList == null || vp9DecodecList == null) getDecodecList();
    ArrayList<String> allHardNormalDecodec;
    if (Objects.equals(mime, MediaFormat.MIMETYPE_VIDEO_HEVC)) allHardNormalDecodec = hevcDecodecList;
    else if (Objects.equals(mime, MediaFormat.MIMETYPE_VIDEO_VP8)) allHardNormalDecodec = vp8DecodecList;
    else if (Objects.equals(mime, MediaFormat.MIMETYPE_VIDEO_VP9)) allHardNormalDecodec = vp9DecodecList;
    else allHardNormalDecodec = avcDecodecList;
    ArrayList<String> allHardLowLatencyDecodec = new ArrayList<>();
    for (String codecName : allHardNormalDecodec) if (codecName.contains("low_latency")) allHardLowLatencyDecodec.add(codecName);
    // 存在低延迟解码器
    if (allHardLowLatencyDecodec.size() > 0) return getC2Decodec(allHardLowLatencyDecodec);
    // 选择正常解码器
    if (allHardNormalDecodec.size() > 0) return getC2Decodec(allHardNormalDecodec);
    return "";
  }

  // 优选C2解码器
  private static String getC2Decodec(ArrayList<String> allHardDecodec) {
    for (String codecName : allHardDecodec) if (codecName.contains("c2")) return codecName;
    return allHardDecodec.get(0);
  }
}
