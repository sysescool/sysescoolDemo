package com.webrtc.android.avcall.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.webrtc.android.avcall.R;
import com.webrtc.android.avcall.service.MediaService;
import com.webrtc.android.avcall.signal.SignalClient;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.Logging;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.MediaStreamTrack;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RendererCommon;
import org.webrtc.RtpParameters;
import org.webrtc.RtpReceiver;
import org.webrtc.RtpSender;
import org.webrtc.RtpTransceiver;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import pub.devrel.easypermissions.EasyPermissions;

public class CallActivity extends AppCompatActivity {

    private static final int VIDEO_RESOLUTION_WIDTH = 1280;
    private static final int VIDEO_RESOLUTION_HEIGHT = 720;
    private static final int VIDEO_FPS = 30;

    private String mState = "init";

    private TextView mLogcatView;
    private Button mBtnMic;
    private Button mBtnCam;
    private Boolean mIsServer;
    private static final String TAG = "CallActivity";

    public static final String VIDEO_TRACK_ID = "1";//"ARDAMSv0";
    public static final String AUDIO_TRACK_ID = "2";//"ARDAMSa0";

    //用于数据传输
    private PeerConnection mPeerConnection = null;
    private PeerConnectionFactory mPeerConnectionFactory;

    //OpenGL ES
    private EglBase mRootEglBase;
    //纹理渲染
    private SurfaceTextureHelper mSurfaceTextureHelper;

    //继承自 surface view
    private SurfaceViewRenderer mLocalSurfaceView;
    private SurfaceViewRenderer mRemoteSurfaceView;

    private VideoTrack mVideoTrack;
    private AudioTrack mAudioTrack;
    private boolean mAudioTrackAdded = false;
    private boolean mVideoTrackAdded = false;
    private VideoCapturer mVideoCapturer;
    private void camBtnDown() {
        String[] perms = {Manifest.permission.CAMERA}; //, Manifest.permission.RECORD_AUDIO};
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, "Need permissions for camera", 0, perms);
        }
        if (mPeerConnection != null && EasyPermissions.hasPermissions(this, perms)) {

            RtpTransceiver transceiver = mPeerConnection.getTransceivers().get(0);

            logcatOnUI("camBtn: videoTrack Added!");
            List<String> mediaStreamLabels = Collections.singletonList("ARDAMSv0");
            mPeerConnection.addTrack(mVideoTrack, mediaStreamLabels);
            mVideoTrackAdded = true;
            mVideoTrack.setEnabled(true);
            doStartCall();
        }
//        if (EasyPermissions.hasPermissions(this, perms) && mVideoTrackAdded) {
//            mVideoTrack.setEnabled(true);
//        }
    }
    private void camBtnUp() {
        if (mPeerConnection != null) {
            List<RtpSender> senders = mPeerConnection.getSenders();
            for(int i = 0; i < senders.size(); i++) {
                RtpSender sender = senders.get(i);
                if (sender != null) {
                    MediaStreamTrack track = sender.track();
                    if (track != null) {
                        String kind = track.kind();
                        if (kind != null) {
                            Log.d(TAG, "Btn-sender " + i + " kind is: " + kind);
                            if (kind.equals("video")) {
                                Log.d(TAG, "Btn-video " + i + " kind sender is deleted.");
                                mPeerConnection.removeTrack(sender);
                            }
                        } else {
                            Log.d(TAG, "Btn-kind " + i + " is null.");
                        }
                    } else {
                        Log.d(TAG, "Btn-track " + i + " is null.");
                    }
                } else {
                    Log.d(TAG, "Btn-sender " + i + " is null.");
                }
            }
        }
//        mPeerConnection.removeTrack(senders.get(i));
//        mVideoTrack.setEnabled(false);
//        mVideoTrack.addSink(mLocalSurfaceView);
    }
    private void micBtnDown() {
        String[] perms = {Manifest.permission.RECORD_AUDIO}; //, Manifest.permission.RECORD_AUDIO};
        if (!EasyPermissions.hasPermissions(this, perms)) {
            EasyPermissions.requestPermissions(this, "Need permissions for microphone", 0, perms);
        }
        if (EasyPermissions.hasPermissions(this, perms) && !mAudioTrackAdded) {
            logcatOnUI("micBtn: audioTrack Added!");
            List<String> mediaStreamLabels = Collections.singletonList("ARDAMSa0");
            mPeerConnection.addTrack(mAudioTrack, mediaStreamLabels);
            mAudioTrackAdded = true;
            doStartCall();
        }
        if (EasyPermissions.hasPermissions(this, perms) && mAudioTrackAdded) {
            mAudioTrack.setEnabled(true);
        }
    }
    private void micBtnUp() {
        mAudioTrack.setEnabled(false);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == 29) {
            if(resultCode == RESULT_OK) {
                startScreenSharing(true, resultCode, data);
            } else {
                Log.e(TAG,"Screen sharing permission denied!");
            }
        }
    }
    public VideoCapturer createScreenCapturer(Intent mMediaProjectionPermissionResultData, int mMediaProjectionPermissionResultCode) {
        if (mMediaProjectionPermissionResultCode != Activity.RESULT_OK) {
            return null;
        }
        return new ScreenCapturerAndroid(
                mMediaProjectionPermissionResultData, new MediaProjection.Callback() {
            @Override
            public void onStop() {
            }
        });
    }
    private void startScreenSharing(Boolean isScreenSharing, int mMediaProjectionPermissionResultCode, Intent mMediaProjectionPermissionResultData) {
        if (isScreenSharing) {
            mVideoCapturer = createScreenCapturer(mMediaProjectionPermissionResultData, mMediaProjectionPermissionResultCode);
            if (mVideoCapturer != null) {
                mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
                VideoSource videoSource = mPeerConnectionFactory.createVideoSource(mIsServer);
                mVideoCapturer.initialize(mSurfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());
                mVideoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS);

                mVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
                mVideoTrack.setEnabled(true);
                mVideoTrack.addSink(mLocalSurfaceView);

                AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
                mAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
                mAudioTrack.setEnabled(true);

                SignalClient.getInstance().setSignalEventListener(mOnSignalEventListener);

                String serverAddr = getIntent().getStringExtra("ServerAddr");
                String roomName = getIntent().getStringExtra("RoomName");
                SignalClient.getInstance().joinRoom(serverAddr, roomName);
            }
        } else {
            Log.e(TAG, "mVideoCapturer is NULL!!!");
        }
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        mIsServer = getIntent().getStringExtra("IsServer").equals("true");
        Log.d(TAG, "is_Server: " + mIsServer);

        mLogcatView = findViewById(R.id.LogcatView);
        mLogcatView.setMovementMethod(ScrollingMovementMethod.getInstance());
        mRootEglBase = EglBase.create();
        mBtnMic = findViewById( R.id.Mic );
        mBtnCam = findViewById( R.id.Cam );

        mLocalSurfaceView = (!mIsServer) ? findViewById(R.id.RemoteSurfaceView) : findViewById(R.id.LocalSurfaceView);
        mLocalSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mLocalSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mLocalSurfaceView.setMirror(!mIsServer);
        mLocalSurfaceView.setEnableHardwareScaler(false /* enabled */);
        if(!mIsServer) mLocalSurfaceView.setZOrderMediaOverlay(true);

        mRemoteSurfaceView = mIsServer ? findViewById(R.id.RemoteSurfaceView) : findViewById(R.id.LocalSurfaceView);
        mRemoteSurfaceView.init(mRootEglBase.getEglBaseContext(), null);
        mRemoteSurfaceView.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL);
        mRemoteSurfaceView.setMirror(mIsServer);
        mRemoteSurfaceView.setEnableHardwareScaler(true /* enabled */);
        if(mIsServer) mRemoteSurfaceView.setZOrderMediaOverlay(true);
        mBtnCam.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        mBtnCam.setBackgroundResource(R.drawable.mic_btn_press_shape);
                        camBtnDown();
                        break;
                    case MotionEvent.ACTION_UP:
                        mBtnCam.setBackgroundResource(R.drawable.mic_btn_nopress_shape);
                        camBtnUp();
                        break;
                }
                return false;
            }
        });
        mBtnMic.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch ( event.getAction() ) {
                    case MotionEvent.ACTION_DOWN:
                        mBtnMic.setBackgroundResource(R.drawable.mic_btn_press_shape);
                        micBtnDown();
                        break;
                    case MotionEvent.ACTION_UP:
                        mBtnMic.setBackgroundResource(R.drawable.mic_btn_nopress_shape);
                        micBtnUp();
                        break;
                }
                return false;
            }
        });
        //创建 factory， pc是从factory里获得的
        mPeerConnectionFactory = createPeerConnectionFactory(this);

        // NOTE: this _must_ happen while PeerConnectionFactory is alive!
//        Logging.enableLogToDebugOutput(Logging.Severity.LS_VERBOSE);
        Logging.enableLogToDebugOutput(Logging.Severity.LS_INFO);

        if (mIsServer) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(new Intent(this, MediaService.class));
            } else {
                startService(new Intent(this, MediaService.class));
            }
            MediaProjectionManager mediaProjectionManager = (MediaProjectionManager)getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            startActivityForResult(mediaProjectionManager.createScreenCaptureIntent(), 29);
        } else {
            mVideoCapturer = createVideoCapturer();

            mSurfaceTextureHelper = SurfaceTextureHelper.create("CaptureThread", mRootEglBase.getEglBaseContext());
            VideoSource videoSource = mPeerConnectionFactory.createVideoSource(false);
            mVideoCapturer.initialize(mSurfaceTextureHelper, getApplicationContext(), videoSource.getCapturerObserver());

            mVideoTrack = mPeerConnectionFactory.createVideoTrack(VIDEO_TRACK_ID, videoSource);
            mVideoTrack.setEnabled(true);
            mVideoTrack.addSink(mLocalSurfaceView);

            AudioSource audioSource = mPeerConnectionFactory.createAudioSource(new MediaConstraints());
            mAudioTrack = mPeerConnectionFactory.createAudioTrack(AUDIO_TRACK_ID, audioSource);
            mAudioTrack.setEnabled(true);

            SignalClient.getInstance().setSignalEventListener(mOnSignalEventListener);

            String serverAddr = getIntent().getStringExtra("ServerAddr");
            String roomName = getIntent().getStringExtra("RoomName");
            SignalClient.getInstance().joinRoom(serverAddr, roomName);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!mIsServer) {
            mVideoCapturer.startCapture(VIDEO_RESOLUTION_WIDTH, VIDEO_RESOLUTION_HEIGHT, VIDEO_FPS);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            if (!mIsServer)
                mVideoCapturer.stopCapture();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        doLeave();
        stopService(new Intent(this, MediaService.class));
        mLocalSurfaceView.release();
        mRemoteSurfaceView.release();
        if (!mIsServer)
            mVideoCapturer.dispose();
        mSurfaceTextureHelper.dispose();
        PeerConnectionFactory.stopInternalTracingCapture();
        PeerConnectionFactory.shutdownInternalTracer();
        mPeerConnectionFactory.dispose();
    }

    public static class SimpleSdpObserver implements SdpObserver {
        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {
            Log.i(TAG, "SdpObserver: onCreateSuccess!");
        }

        @Override
        public void onSetSuccess() {
            Log.i(TAG, "SdpObserver: onSetSuccess");
        }

        @Override
        public void onCreateFailure(String msg) {
            Log.e(TAG, "SdpObserver onCreateFailure: " + msg);
        }

        @Override
        public void onSetFailure(String msg) {
            Log.e(TAG, "SdpObserver onSetFailure: " + msg);
        }
    }

    private void updateCallState(boolean idle) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                if (idle) {
                    mRemoteSurfaceView.setVisibility(View.GONE);
                } else {
                    mRemoteSurfaceView.setVisibility(View.VISIBLE);
                }
            }
        });
    }

    public void doStartCall() {
        logcatOnUI("Start Call, Wait ...");
        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }
        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        mediaConstraints.optional.add(new MediaConstraints.KeyValuePair("DtlsSrtpKeyAgreement", "true"));
        mPeerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.i(TAG, "OAA-Create local offer success: \n" + sessionDescription.description);
                logcatOnUI("SdpObserver: onCreateSuccess!");
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                JSONObject message = new JSONObject();
                try {
                    message.put("type", "offer");
                    message.put("sdp", sessionDescription.description);
                    SignalClient.getInstance().sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, mediaConstraints);
    }

    public void doLeave() {
        logcatOnUI("Leave room, Wait ...");
        hangup();

        SignalClient.getInstance().leaveRoom();

    }

    public void doAnswerCall() {
        logcatOnUI("Answer Call, Wait ...");

        if (mPeerConnection == null) {
            mPeerConnection = createPeerConnection();
        }

        MediaConstraints sdpMediaConstraints = new MediaConstraints();
        Log.i(TAG, "Create answer ...");
        mPeerConnection.createAnswer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                Log.i(TAG, "OAA-Create answer success: \n" + sessionDescription.description);
                mPeerConnection.setLocalDescription(new SimpleSdpObserver(),
                                                    sessionDescription);

                JSONObject message = new JSONObject();
                try {
                    message.put("type", "answer");
                    message.put("sdp", sessionDescription.description);
                    SignalClient.getInstance().sendMessage(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }, sdpMediaConstraints);
        updateCallState(false);
    }

    private void hangup() {
        logcatOnUI("Hangup Call, Wait ...");
        if (mPeerConnection == null) {
            return;
        }
        mPeerConnection.close();
        mPeerConnection = null;
        logcatOnUI("Hangup Done.");
        updateCallState(true);
    }

    public PeerConnection createPeerConnection() {
        Log.i(TAG, "Create PeerConnection ...");
        logcatOnUI("Create PeerConnection ...");
        LinkedList<PeerConnection.IceServer> iceServers = new LinkedList<PeerConnection.IceServer>();

        PeerConnection.IceServer ice_server =
                    PeerConnection.IceServer.builder(     getResources().getString(R.string.STUN_SERVER) )
                                            .setPassword( getResources().getString(R.string.STUN_PASWOD) )
                                            .setUsername( getResources().getString(R.string.STUN_USRNAM) )
                                            .createIceServer();

        iceServers.add(ice_server);

        PeerConnection.RTCConfiguration rtcConfig = new PeerConnection.RTCConfiguration(iceServers);
        // TCP candidates are only useful when connecting to a server that supports
        // ICE-TCP.
        rtcConfig.tcpCandidatePolicy = PeerConnection.TcpCandidatePolicy.DISABLED;
        //rtcConfig.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE;
        //rtcConfig.rtcpMuxPolicy = PeerConnection.RtcpMuxPolicy.REQUIRE;
        rtcConfig.continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY;
        // Use ECDSA encryption.
        //rtcConfig.keyType = PeerConnection.KeyType.ECDSA;
        // Enable DTLS for normal calls and disable for loopback calls.
//        rtcConfig.enableDtlsSrtp = true;
        //rtcConfig.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN;
        PeerConnection connection =
                mPeerConnectionFactory.createPeerConnection(rtcConfig,
                                                            mPeerConnectionObserver);
        if (connection == null) {
            Log.e(TAG, "Failed to createPeerConnection!");
            logcatOnUI("Failed to createPeerConnection!");
            return null;
        }


        if (mIsServer) {
            List<String> mediaStreamLabels = Collections.singletonList("ARDAMS");
            connection.addTrack(mVideoTrack, mediaStreamLabels);
        }
        // connection.addTrack(mAudioTrack, mediaStreamLabels);

        return connection;
    }

    public PeerConnectionFactory createPeerConnectionFactory(Context context) {
        final VideoEncoderFactory encoderFactory;
        final VideoDecoderFactory decoderFactory;
        if(mIsServer) {
            encoderFactory = new DefaultVideoEncoderFactory(
                    mRootEglBase.getEglBaseContext(),
                    true /* enableIntelVp8Encoder */,
                    false);
        }
        else {
            encoderFactory = new DefaultVideoEncoderFactory(
                    mRootEglBase.getEglBaseContext(),
                    false /* enableIntelVp8Encoder */,
                    true);
        }
        decoderFactory = new DefaultVideoDecoderFactory(mRootEglBase.getEglBaseContext());

        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions());

        PeerConnectionFactory.Builder builder = PeerConnectionFactory.builder()
                .setVideoEncoderFactory(encoderFactory)
                .setVideoDecoderFactory(decoderFactory);
        builder.setOptions(null);

        return builder.createPeerConnectionFactory();
    }

    /*
     * Read more about Camera2 here
     * https://developer.android.com/reference/android/hardware/camera2/package-summary.html
     **/
    private VideoCapturer createVideoCapturer() {
        if (Camera2Enumerator.isSupported(this)) {
            return createCameraCapturer(new Camera2Enumerator(this));
        } else {
            return createCameraCapturer(new Camera1Enumerator(true));
        }
    }

    private VideoCapturer createCameraCapturer(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();

        // First, try to find front facing camera
        Log.d(TAG, "Looking for front facing cameras.");
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating front facing camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }

        // Front facing camera not found, try something else
        Log.d(TAG, "Looking for other cameras.");
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                Logging.d(TAG, "Creating other camera capturer.");
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private PeerConnection.Observer mPeerConnectionObserver = new PeerConnection.Observer() {
        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {
            Log.i(TAG, "onSignalingChange: " + signalingState);
            logcatOnUI("PcObserver: onSignalingChange");
        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
            Log.i(TAG, "onIceConnectionChange: " + iceConnectionState);
            logcatOnUI("PcObserver: onIceConnectionChange");
        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {
            Log.i(TAG, "onIceConnectionReceivingChange: " + b);
            logcatOnUI("PcObserver: onIceConnectionReceivingChange");
        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
            Log.i(TAG, "onIceGatheringChange: " + iceGatheringState);
            logcatOnUI("PcObserver: onIceGatheringChange");

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {
            Log.i(TAG, "onIceCandidate: " + iceCandidate);
            logcatOnUI("PcObserver: onIceCandidate");
            try {
                JSONObject message = new JSONObject();
                //message.put("userId", RTCSignalClient.getInstance().getUserId());
                message.put("type", "candidate");
                message.put("label", iceCandidate.sdpMLineIndex);
                message.put("id", iceCandidate.sdpMid);
                message.put("candidate", iceCandidate.sdp);
                SignalClient.getInstance().sendMessage(message);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
            for (int i = 0; i < iceCandidates.length; i++) {
                Log.i(TAG, "onIceCandidatesRemoved: " + iceCandidates[i]);
                logcatOnUI("PcObserver: onIceCandidatesRemoved:" + i);
            }
            mPeerConnection.removeIceCandidates(iceCandidates);
        }

        @Override
        public void onAddStream(MediaStream mediaStream) {
            Log.i(TAG, "onAddStream: " + mediaStream.videoTracks.size());
            logcatOnUI("PcObserver: onAddStream");
        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {
            Log.i(TAG, "onRemoveStream");
            logcatOnUI("PcObserver: onRemoveStream");
        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {
            Log.i(TAG, "onDataChannel");
            logcatOnUI("PcObserver: onDataChannel");
        }

        @Override
        public void onRenegotiationNeeded() {
            Log.i(TAG, "onRenegotiationNeeded");
            logcatOnUI("PcObserver: onRenegotiationNeeded");
        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
            MediaStreamTrack track = rtpReceiver.track();
            if (track instanceof VideoTrack) {
                Log.i(TAG, "onAddVideoTrack");
                logcatOnUI("PcObserver: onAddVideoTrack");
                VideoTrack remoteVideoTrack = (VideoTrack) track;
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(mRemoteSurfaceView);
            }
            if (track instanceof AudioTrack) {
                Log.i(TAG, "onAddAudioTrack");
                logcatOnUI("PcObserver: onAddAudioTrack");
                AudioTrack remoteAudioTrack = (AudioTrack) track;
                remoteAudioTrack.setEnabled(true);
                //remoteAudioTrack.setVolume(9.0);
            }
        }
    };

    private SignalClient.OnSignalEventListener
            mOnSignalEventListener = new SignalClient.OnSignalEventListener() {

        @Override
        public void onConnected() {

            logcatOnUI("Signal Server Connected !");
        }

        @Override
        public void onConnecting() {

            logcatOnUI("Signal Server Connecting !");
        }

        @Override
        public void onDisconnected() {

            logcatOnUI("Signal Server Disconnected!");
        }

        @Override
        public void onUserJoined(String roomName, String userID){

            logcatOnUI("local user joined!");

            mState = "joined";

            //这里应该创建PeerConnection
            if (mPeerConnection == null) {
                mPeerConnection = createPeerConnection();
            }
        }

        @Override
        public void onUserLeaved(String roomName, String userID){
            logcatOnUI("local user leaved!");

            mState = "leaved";
        }

        @Override
        public void onRemoteUserJoined(String roomName) {
            logcatOnUI("Remote User Joined, room: " + roomName);

            if(mState.equals("joined_unbind")){
                if (mPeerConnection == null) {
                    mPeerConnection = createPeerConnection();
                }
            }

            mState = "joined_conn";
            //调用call， 进行媒体协商
            doStartCall();
        }

        @Override
        public void onRemoteUserLeaved(String roomName, String userID) {
            logcatOnUI("Remote User Leaved, room: " + roomName + "uid:"  + userID);
            mState = "joined_unbind";

            if(mPeerConnection !=null ){
                mPeerConnection.close();
                mPeerConnection = null;
            }
        }

        @Override
        public void onRoomFull(String roomName, String userID){
            logcatOnUI("The Room is Full, room: " + roomName + "uid:"  + userID);
            mState = "leaved";

            if(mLocalSurfaceView != null) {
                mLocalSurfaceView.release();
                mLocalSurfaceView = null;
            }

            if(mRemoteSurfaceView != null) {
                mRemoteSurfaceView.release();
                mRemoteSurfaceView = null;
            }

            if(mVideoCapturer != null && !mIsServer) {
                mVideoCapturer.dispose();
                mVideoCapturer = null;
            }

            if(mSurfaceTextureHelper != null) {
                mSurfaceTextureHelper.dispose();
                mSurfaceTextureHelper = null;

            }

            PeerConnectionFactory.stopInternalTracingCapture();
            PeerConnectionFactory.shutdownInternalTracer();

            if(mPeerConnectionFactory !=null) {
                mPeerConnectionFactory.dispose();
                mPeerConnectionFactory = null;
            }

            finish();
        }

        @Override
        public void onMessage(JSONObject message) {

            Log.i(TAG, "onMessage: " + message);
            try {
                logcatOnUI("onMessage: " + message.getString("type"));
                String type = message.getString("type");
                if (type.equals("offer")) {
                    onRemoteOfferReceived(message);
                }else if(type.equals("answer")) {
                    onRemoteAnswerReceived(message);
                }else if(type.equals("candidate")) {
                    onRemoteCandidateReceived(message);
                }else{
                    Log.w(TAG, "the type is invalid: " + type);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void onRemoteOfferReceived(JSONObject message) {
            logcatOnUI("Receive Remote Call ...");

            if (mPeerConnection == null) {
                mPeerConnection = createPeerConnection();
            }

            try {
                String description = message.getString("sdp");
                SessionDescription sdp = new SessionDescription(
                                            SessionDescription.Type.OFFER,
                                            description);
                mPeerConnection.setRemoteDescription(
                                            new SimpleSdpObserver(),
                                            sdp);
                Log.d(TAG, "Msg-onRemoteOfferReceived: " + sdp.description);
                doAnswerCall();
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void onRemoteAnswerReceived(JSONObject message) {
            logcatOnUI("Receive Remote Answer ...");
            try {
                String description = message.getString("sdp");
                SessionDescription sdp = new SessionDescription(
                                    SessionDescription.Type.ANSWER,
                                    description);
                mPeerConnection.setRemoteDescription(
                                    new SimpleSdpObserver(),
                                    sdp);
                Log.d(TAG, "Msg-onRemoteAnswerReceived: " + sdp.description);
            } catch (JSONException e) {
                e.printStackTrace();
            }
            updateCallState(false);
        }

        private void onRemoteCandidateReceived(JSONObject message) {
            logcatOnUI("Receive Remote Candidate ...");
            try {
                IceCandidate remoteIceCandidate =
                        new IceCandidate(message.getString("id"),
                                            message.getInt("label"),
                                            message.getString("candidate"));
                Log.d(TAG, "Msg-onRemoteCandidateReceived: " + remoteIceCandidate.sdp);
                mPeerConnection.addIceCandidate(remoteIceCandidate);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

        private void onRemoteHangup() {
            logcatOnUI("Receive Remote Hangup Event ...");
            hangup();
        }
    };

    private void logcatOnUI(String msg) {
        Log.i(TAG, msg);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                String output = mLogcatView.getText() + "\n> " + msg;
                mLogcatView.setText(output);
            }
        });
    }
}
