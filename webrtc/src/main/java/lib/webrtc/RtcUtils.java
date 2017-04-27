package lib.webrtc;

import android.app.Application;
import android.content.pm.PackageManager;
import android.util.Log;

import org.appspot.apprtc.AppRTCAudioManager;
import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.DirectRTCClient;
import org.appspot.apprtc.PeerConnectionClient;
import org.appspot.apprtc.ProxyRenderer;
import org.appspot.apprtc.WebSocketRTCClient;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.SessionDescription;
import org.webrtc.StatsReport;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import rx.Observable;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Action1;

/**
 * RTC调用方法
 *
 * @author GP
 * @date 2017/4/26.
 */

public enum RtcUtils implements AppRTCClient.SignalingEvents, PeerConnectionClient.PeerConnectionEvents {

    INSTANCE,;

    // List of mandatory application permissions.
    private static final String[] MANDATORY_PERMISSIONS = {
            "android.permission.MODIFY_AUDIO_SETTINGS",
            "android.permission.RECORD_AUDIO", "android.permission.INTERNET"
    };

    private Application mContext;
    private List<VideoRenderer.Callbacks> remoteRenderers;
    private AppRTCClient.RoomConnectionParameters roomConnectionParameters;
    private PeerConnectionClient.PeerConnectionParameters peerConnectionParameters;
    private ProxyRenderer remoteProxyRenderer;
    private ProxyRenderer localProxyRenderer;
    private EglBase rootEglBase;
    private SurfaceViewRenderer nowSurfaceViewRenderer;
    private AppRTCClient appRtcClient;
    private AppRTCClient.SignalingParameters signalingParameters;
    private PeerConnectionClient peerConnectionClient = null;
    private AppRTCAudioManager audioManager = null;
    private long callStartedTimeMs = 0;
    private String TAG = "RtcUtils";
    private static final int STAT_CALLBACK_PERIOD = 1000;

    public boolean isActive = false;

    public void init(Application context) {
        if (nowSurfaceViewRenderer != null) {
            destroy();
        }
        mContext = context;
        remoteRenderers = new ArrayList<VideoRenderer.Callbacks>();
        remoteProxyRenderer = new ProxyRenderer();
        localProxyRenderer = new ProxyRenderer();
        remoteRenderers.add(remoteProxyRenderer);
        rootEglBase = EglBase.create();
        isActive = true;
    }

    public void startRTC(String url, String roomId, SurfaceViewRenderer surfaceViewRenderer) {
        callStartedTimeMs = System.currentTimeMillis();
        nowSurfaceViewRenderer = surfaceViewRenderer;
        RTCSetting rtcSetting = new RTCSetting(url, roomId);
        surfaceViewRenderer.init(rootEglBase.getEglBaseContext(), null);
        surfaceViewRenderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        surfaceViewRenderer.setEnableHardwareScaler(true /* enabled */);
        //        localProxyRenderer.setTarget(surfaceViewRenderer);
        remoteProxyRenderer.setTarget(surfaceViewRenderer);
        surfaceViewRenderer.setMirror(false);
        // Check for mandatory permissions.
        for (String permission : MANDATORY_PERMISSIONS) {
            if (mContext.checkCallingOrSelfPermission(permission)
                    != PackageManager.PERMISSION_GRANTED) {
                Log.e("", "Permission " + permission + " is not granted");
            }
        }

        PeerConnectionClient.DataChannelParameters dataChannelParameters =
                new PeerConnectionClient.DataChannelParameters(rtcSetting.ordered,
                        rtcSetting.maxRetrMs,
                        rtcSetting.maxRetr,
                        rtcSetting.protocol,
                        rtcSetting.negotiated,
                        rtcSetting.id);

        peerConnectionParameters =
                new PeerConnectionClient.PeerConnectionParameters(rtcSetting.videoCallEnabled,
                        rtcSetting.loopback,
                        rtcSetting.tracing,
                        rtcSetting.videoWidth,
                        rtcSetting.videoHeight,
                        rtcSetting.cameraFps,
                        rtcSetting.videoStartBitrate,
                        rtcSetting.videoCodec,
                        rtcSetting.hwCodec,
                        rtcSetting.flexfecEnabled,
                        rtcSetting.audioStartBitrate,
                        rtcSetting.audioCodec,
                        rtcSetting.noAudioProcessing,
                        rtcSetting.aecDump,
                        rtcSetting.useOpenSLES,
                        rtcSetting.disableBuiltInAEC,
                        rtcSetting.disableBuiltInAGC,
                        rtcSetting.disableBuiltInNS,
                        rtcSetting.enableLevelControl,
                        dataChannelParameters);

        // Create connection client. Use DirectRTCClient if room name is an IP otherwise use the
        // standard WebSocketRTCClient.
        if (rtcSetting.loopback || !DirectRTCClient.IP_PATTERN.matcher(roomId).matches()) {
            appRtcClient = new WebSocketRTCClient(this);
        } else {
            Log.i("RtcUtils", "Using DirectRTCClient because room name looks like an IP.");
            appRtcClient = new DirectRTCClient(this);
        }

        // Create connection parameters.
        roomConnectionParameters =
                new AppRTCClient.RoomConnectionParameters(url, roomId, rtcSetting.loopback);

        peerConnectionClient = PeerConnectionClient.getInstance();
        if (rtcSetting.loopback) {
            PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
            options.networkIgnoreMask = 0;
            peerConnectionClient.setPeerConnectionFactoryOptions(options);
        }
        peerConnectionClient.createPeerConnectionFactory(
                mContext.getApplicationContext(), peerConnectionParameters, this);

        if (appRtcClient == null) {
            Log.e("RtcUtils", "AppRTC client is not allocated for a call.");
            return;
        }

        callStartedTimeMs = System.currentTimeMillis();

        // Start room connection.
        appRtcClient.connectToRoom(roomConnectionParameters);

        // Create and audio manager that will take care of audio routing,
        // audio modes, audio device enumeration etc.
        audioManager = AppRTCAudioManager.create(mContext.getApplicationContext());
        // Store existing audio settings and change audio mode to
        // MODE_IN_COMMUNICATION for best possible VoIP performance.
        Log.d("RtcUtils", "Starting the audio manager...");
        audioManager.start(new AppRTCAudioManager.AudioManagerEvents() {
            // This method will be called each time the number of available audio
            // devices has changed.
            @Override
            public void onAudioDeviceChanged(
                    AppRTCAudioManager.AudioDevice audioDevice,
                    Set<AppRTCAudioManager.AudioDevice> availableAudioDevices) {
                onAudioManagerDevicesChanged(audioDevice, availableAudioDevices);
            }
        });
    }

    // Disconnect from remote resources, dispose of local resources, and exit.
    public void destroy() {
        try {
            isActive = false;
            remoteProxyRenderer.setTarget(null);
            localProxyRenderer.setTarget(null);
            rootEglBase.release();
            if (appRtcClient != null) {
                appRtcClient.disconnectFromRoom();
                appRtcClient = null;
            }
            if (peerConnectionClient != null) {
                peerConnectionClient.close();
                peerConnectionClient = null;
            }
            if (nowSurfaceViewRenderer != null) {
                nowSurfaceViewRenderer.release();
                nowSurfaceViewRenderer = null;
            }
            if (audioManager != null) {
                audioManager.stop();
                audioManager = null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onLocalDescription(SessionDescription sdp) {
        Log.e(TAG, "onLocalDescription");
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Observable.just(sdp)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SessionDescription>() {
                    @Override
                    public void call(SessionDescription sessionDescription) {
                        if (appRtcClient != null) {
                            Log.e(TAG, "Sending " + sessionDescription.type + ", delay=" + delta + "ms");
                            if (signalingParameters.initiator) {
                                appRtcClient.sendOfferSdp(sessionDescription);
                            } else {
                                appRtcClient.sendAnswerSdp(sessionDescription);
                            }
                        }
                        if (peerConnectionParameters.videoMaxBitrate > 0) {
                            Log.d(TAG, "Set video maximum bitrate: "
                                    + peerConnectionParameters.videoMaxBitrate);
                            peerConnectionClient.setVideoMaxBitrate(
                                    peerConnectionParameters.videoMaxBitrate);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onIceCandidate(IceCandidate candidate) {
        Log.e(TAG, "onIceCandidate");
        Observable.just(candidate)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<IceCandidate>() {
                    @Override
                    public void call(IceCandidate iceCandidate) {
                        if (appRtcClient != null) {
                            appRtcClient.sendLocalIceCandidate(iceCandidate);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.e(TAG, "onIceCandidatesRemoved");
        Observable.just(candidates)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<IceCandidate[]>() {
                    @Override
                    public void call(IceCandidate[] iceCandidates) {
                        if (appRtcClient != null) {
                            appRtcClient.sendLocalIceCandidateRemovals(iceCandidates);
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onIceConnected() {
        Log.e(TAG, "onIceConnected");
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Observable.just("")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        Log.e(TAG, "ICE connected, delay=" + delta + "ms");
                        final long delta = System.currentTimeMillis() - callStartedTimeMs;
                        Log.i(TAG, "Call connected: delay=" + delta + "ms");
                        if (peerConnectionClient == null) {
                            Log.w(TAG, "Call is connected in closed or error state");
                            return;
                        }
                        // Enable statistics callback.
                        peerConnectionClient.enableStatsEvents(true, STAT_CALLBACK_PERIOD);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onIceDisconnected() {
        Log.e(TAG, "onIceDisconnected");
        Observable.just("")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        destroy();
                    }
                });
    }

    @Override
    public void onPeerConnectionClosed() {
        Log.e(TAG, "onPeerConnectionClosed");
    }

    @Override
    public void onPeerConnectionStatsReady(StatsReport[] reports) {
        Log.e(TAG, "onPeerConnectionStatsReady");
    }

    @Override
    public void onPeerConnectionError(String description) {
        Log.e(TAG, "onPeerConnectionError:" + description);
    }

    @Override
    public void onConnectedToRoom(AppRTCClient.SignalingParameters params) {
        Log.e(TAG, "onConnectedToRoom");
        Observable.just(params)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<AppRTCClient.SignalingParameters>() {
                    @Override
                    public void call(AppRTCClient.SignalingParameters params) {
                        final long delta = System.currentTimeMillis() - callStartedTimeMs;
                        signalingParameters = params;
                        Log.e("RtcUtils", "Creating peer connection, delay=" + delta + "ms");
                        VideoCapturer videoCapturer = null;
                        if (peerConnectionParameters.videoCallEnabled) {
                            Logging.d("RtcUtils", "Creating capturer using camera1 API.");
                            videoCapturer = createCameraCapturer(new Camera1Enumerator(true));
                            if (videoCapturer == null) {
                                Logging.e("RtcUtils", "Failed to open camera");
                                return;
                            }
                        }
                        peerConnectionClient.createPeerConnection(rootEglBase.getEglBaseContext(),
                                localProxyRenderer,
                                remoteRenderers, videoCapturer, signalingParameters);
                        Log.e(TAG, "signalingParameters:" + signalingParameters.initiator);
                        if (signalingParameters.initiator) {
                            Log.e("RtcUtils", "Creating OFFER...");
                            // Create offer. Offer SDP will be sent to answering client in
                            // PeerConnectionEvents.onLocalDescription event.
                            peerConnectionClient.createOffer();
                        } else {
                            if (params.offerSdp != null) {
                                peerConnectionClient.setRemoteDescription(params.offerSdp);
                                Log.e("RtcUtils", "Creating ANSWER...");
                                // Create answer. Answer SDP will be sent to offering client in
                                // PeerConnectionEvents.onLocalDescription event.
                                peerConnectionClient.createAnswer();
                            }
                            if (params.iceCandidates != null) {
                                // Add remote ICE candidates from room.
                                for (IceCandidate iceCandidate : params.iceCandidates) {
                                    peerConnectionClient.addRemoteIceCandidate(iceCandidate);
                                }
                            }
                        }
                    }
                });
    }

    @Override
    public void onRemoteDescription(SessionDescription sdp) {
        Log.e(TAG, "onRemoteDescription");
        final long delta = System.currentTimeMillis() - callStartedTimeMs;
        Observable.just(sdp)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<SessionDescription>() {
                    @Override
                    public void call(SessionDescription sessionDescription) {
                        if (peerConnectionClient == null) {
                            Log.e(TAG, "Received remote SDP for non-initilized peer connection.");
                            return;
                        }
                        Log.e(TAG, "Received remote " + sessionDescription.type + ", delay=" + delta + "ms");
                        peerConnectionClient.setRemoteDescription(sessionDescription);
                        if (!signalingParameters.initiator) {
                            Log.e(TAG, "Creating ANSWER...");
                            // Create answer. Answer SDP will be sent to offering client in
                            // PeerConnectionEvents.onLocalDescription event.
                            peerConnectionClient.createAnswer();
                        }
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        Log.e(TAG, "onRemoteIceCandidate");
        Observable.just(candidate)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<IceCandidate>() {
                    @Override
                    public void call(IceCandidate candidate) {
                        if (peerConnectionClient == null) {
                            Log.e(TAG, "Received ICE candidate for a non-initialized peer connection.");
                            return;
                        }
                        peerConnectionClient.addRemoteIceCandidate(candidate);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onRemoteIceCandidatesRemoved(IceCandidate[] candidates) {
        Log.e(TAG, "onRemoteIceCandidatesRemoved");
        Observable.just(candidates)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<IceCandidate[]>() {
                    @Override
                    public void call(IceCandidate[] iceCandidates) {
                        if (peerConnectionClient == null) {
                            Log.e(TAG,
                                    "Received ICE candidate removals for a non-initialized peer connection.");
                            return;
                        }
                        peerConnectionClient.removeRemoteIceCandidates(iceCandidates);
                    }
                }, new Action1<Throwable>() {
                    @Override
                    public void call(Throwable throwable) {
                        throwable.printStackTrace();
                    }
                });
    }

    @Override
    public void onChannelClose() {
        Log.e(TAG, "onChannelClose");
        Observable.just("")
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Action1<String>() {
                    @Override
                    public void call(String s) {
                        destroy();
                    }
                });
    }

    @Override
    public void onChannelError(String description) {
        Log.e(TAG, "onChannelError:" + description);
    }

    // This method is called when the audio manager reports audio device change,
    // e.g. from wired headset to speakerphone.
    private void onAudioManagerDevicesChanged(
            final AppRTCAudioManager.AudioDevice device,
            final Set<AppRTCAudioManager.AudioDevice> availableDevices) {
        Log.d("RtcUtils", "onAudioManagerDevicesChanged: " + availableDevices + ", "
                + "selected: " + device);
        // TODO(henrika): add callback handler.
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Logging.d("RtcUtils", "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d("RtcUtils", "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);

                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Logging.d("RtcUtils", "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d("RtcUtils", "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }
}
