package lib.webrtc;

import android.text.TextUtils;

import java.util.Random;

/**
 * RTC Setting
 *
 * @author GP
 * @date 2017/4/25.
 */

class RTCSetting {

    RTCSetting(String url, String roomId) {
        if (!TextUtils.isEmpty(url)) {
            roomUri = url;
        }
        if (!TextUtils.isEmpty(roomId)) {
            this.roomId = roomId;
        }
    }

    // url
    public String roomUri = "https://10.0.1.75";
    // roomId
    public String roomId = Integer.toString((new Random()).nextInt(100000000));
    // loopback
    public boolean loopback = false;
    public boolean tracing = false;
    // Video call enabled flag.
    public boolean videoCallEnabled = true;
    // Use screencapture option.
    public boolean useScreencapture = false;
    // Use Camera2 option.
    public boolean useCamera2 = false;
    // Get default codecs.
    public String videoCodec = "VP8";
    public String audioCodec = "OPUS";
    // Check HW codec flag.
    public boolean hwCodec = true;
    // Check Capture to texture.
    public boolean captureToTexture = true;
    // Check FlexFEC.
    public boolean flexfecEnabled = false;
    // Check Disable Audio Processing flag.
    public boolean noAudioProcessing = false;
    // Check Disable Audio Processing flag.
    public boolean aecDump = false;
    // Check OpenSL ES enabled flag.
    public boolean useOpenSLES = false;
    // Check Disable built-in AEC flag.
    public boolean disableBuiltInAEC = false;
    // Check Disable built-in AGC flag.
    public boolean disableBuiltInAGC = false;
    // Check Disable built-in NS flag.
    public boolean disableBuiltInNS = false;
    // Check Enable level control.
    public boolean enableLevelControl = false;
    // Get video resolution from settings.
    public int videoWidth = 640;
    public int videoHeight = 480;
    public int cameraFps = 0;
    // Check capture quality slider flag.
    public boolean captureQualitySlider = false;
    // Get video and audio start bitrate.
    public int videoStartBitrate = 1700;
    public int audioStartBitrate = 32;
    // Check statistics display option.
    public boolean displayHud = false;

    public boolean dataChannelEnabled = true;

    public boolean ordered = true;
    public boolean negotiated = false;
    public int maxRetrMs = -1;
    public int maxRetr = -1;
    public int id = -1;

    public String protocol = "";
}
