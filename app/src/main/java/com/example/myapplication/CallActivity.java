package com.example.myapplication;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.Transaction;

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
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoSink;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CallActivity extends AppCompatActivity {
    private String userUid, friendUid;
    private FirebaseFirestore db = FirebaseFirestore.getInstance();
    private boolean isInitiator = false;

    //request codes
    private int CAMERA_PERMISSION_CODE = 0;

    //views
    SurfaceViewRenderer localVideoView, friendVideoView;

    //webrtc
    private EglBase rootEglBase;
    private PeerConnectionFactory factory;
    private PeerConnection peerConnection;

    private static class SimpleSdpObserver implements SdpObserver{

        @Override
        public void onCreateSuccess(SessionDescription sessionDescription) {

        }

        @Override
        public void onSetSuccess() {

        }

        @Override
        public void onCreateFailure(String s) {

        }

        @Override
        public void onSetFailure(String s) {

        }
    }

    private static class SimplePeerConnectionObserver implements PeerConnection.Observer{

        @Override
        public void onSignalingChange(PeerConnection.SignalingState signalingState) {

        }

        @Override
        public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

        }

        @Override
        public void onIceConnectionReceivingChange(boolean b) {

        }

        @Override
        public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

        }

        @Override
        public void onIceCandidate(IceCandidate iceCandidate) {

        }

        @Override
        public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

        }

        @Override
        public void onAddStream(MediaStream mediaStream) {

        }

        @Override
        public void onRemoveStream(MediaStream mediaStream) {

        }

        @Override
        public void onDataChannel(DataChannel dataChannel) {

        }

        @Override
        public void onRenegotiationNeeded() {

        }

        @Override
        public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_call);

        userUid = getIntent().getStringExtra("userUid");
        friendUid = getIntent().getStringExtra("friendUid");
        isInitiator = getIntent().getBooleanExtra("initiator", false);

        Button endCallBtn = findViewById(R.id.btnEndCall);

        endCallBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onBackPressed();
            }
        });

        checkPermissions();
        initialize();
        setupFireStoreListeners();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == CAMERA_PERMISSION_CODE){
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED  ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED  ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_DENIED)
                finish();
        }
    }

    @Override
    public void onBackPressed() {
        hangup();
    }

    private void initialize()
    {
        //initialize views
        rootEglBase = EglBase.create();

        localVideoView = findViewById(R.id.localVideo);
        localVideoView.init(rootEglBase.getEglBaseContext(), null);
        localVideoView.setEnableHardwareScaler(true);
        localVideoView.setMirror(true);

        friendVideoView = findViewById(R.id.friendVideo);
        friendVideoView.init(rootEglBase.getEglBaseContext(), null);
        friendVideoView.setEnableHardwareScaler(true);
        friendVideoView.setMirror(true);

        //initialize peer connection factory
        PeerConnectionFactory.InitializationOptions initializationOptions = PeerConnectionFactory.InitializationOptions.builder(this)
                .setEnableInternalTracer(true)
                .setFieldTrials("WebRTC-H264HighProfile/Enabled/")
                .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = true;
        options.disableNetworkMonitor = true;

        factory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoDecoderFactory(new DefaultVideoDecoderFactory(rootEglBase.getEglBaseContext()))
                .setVideoEncoderFactory(new DefaultVideoEncoderFactory(rootEglBase.getEglBaseContext(), true, true))
                .createPeerConnectionFactory();

        //create video track form camera and show it
        VideoCapturer videoCapturer = createVideoCapturer();
        if(videoCapturer == null){
            finish();
            return;
        }
        VideoSource videoSource = factory.createVideoSource(false);
        SurfaceTextureHelper surfaceTextureHelper = SurfaceTextureHelper.create(Thread.currentThread().getName(), rootEglBase.getEglBaseContext());
        videoCapturer.initialize(surfaceTextureHelper, localVideoView.getContext(), videoSource.getCapturerObserver());
        videoCapturer.startCapture(1240, 720, 30);
        VideoTrack localVideoTrack = factory.createVideoTrack(userUid + "video", videoSource);
        localVideoTrack.addSink(localVideoView);

        //set ice candidates to null
        db.document("users/" + userUid).update("ice", null);
        db.document("users/" + friendUid).update("ice", null);

        //create peer connection
        ArrayList<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        PeerConnection.Observer pcObserver = new PeerConnection.Observer() {
            @Override
            public void onSignalingChange(PeerConnection.SignalingState signalingState) {

            }

            @Override
            public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {

            }

            @Override
            public void onIceConnectionReceivingChange(boolean b) {

            }

            @Override
            public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {

            }

            @Override
            public void onIceCandidate(IceCandidate iceCandidate) {
                Log.d("WEBRTCD", "Ice");

                db.runTransaction(new Transaction.Function<Void>() {
                    @Nullable
                    @Override
                    public Void apply(@NonNull Transaction transaction) throws FirebaseFirestoreException {
                        List<Map> iceList = (List<Map>) transaction.get(db.document("users/" + friendUid)).get("ice");
                        if(iceList == null) iceList = new ArrayList<>();

                        Map<String, Object> ice = new HashMap<>();
                        ice.put("label", iceCandidate.sdpMLineIndex);
                        ice.put("id", iceCandidate.sdpMid);
                        ice.put("sdp", iceCandidate.sdp);

                        iceList.add(0, ice);
                        transaction.update(db.document("users/" + friendUid), "ice", iceList);

                        return null;
                    }
                });

            }

            @Override
            public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {

            }

            @Override
            public void onAddStream(MediaStream mediaStream) {
                VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
                if(mediaStream.audioTracks.size() > 0) {
                    AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);
                    remoteAudioTrack.setEnabled(true);
                }
                remoteVideoTrack.setEnabled(true);
                remoteVideoTrack.addSink(friendVideoView);

            }

            @Override
            public void onRemoveStream(MediaStream mediaStream) {

            }

            @Override
            public void onDataChannel(DataChannel dataChannel) {

            }

            @Override
            public void onRenegotiationNeeded() {

            }

            @Override
            public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {

            }
        };
        peerConnection = factory.createPeerConnection(iceServers, pcObserver);

        //create audio track
        MediaConstraints audioConstraints = new MediaConstraints();
        AudioSource audioSource = factory.createAudioSource(audioConstraints);
        AudioTrack localAudioTrack = factory.createAudioTrack(userUid + "audio", audioSource);
        //peerConnection.addTrack(localAudioTrack);
        peerConnection.setAudioRecording(true);
        peerConnection.setAudioPlayout(true);

        //add stream to peer connection
        MediaStream mediaStream = factory.createLocalMediaStream(userUid);
        mediaStream.addTrack(localAudioTrack);
        mediaStream.addTrack(localVideoTrack);
        peerConnection.addStream(mediaStream);

        if(isInitiator) doCall();
        else doAnswer();
    }

    private void doCall()
    {
        db.document("users/" + friendUid).update("call", userUid);

        MediaConstraints mediaConstraints = new MediaConstraints();
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
        mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
        peerConnection.createOffer(new SimpleSdpObserver() {
            @Override
            public void onCreateSuccess(SessionDescription sessionDescription) {
                peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                Map<String, String> sdp = new HashMap<>();
                sdp.put("type", "offer");
                sdp.put("desc", sessionDescription.description);
                db.document("users/" + friendUid).update("sdp", sdp);
            }

            @Override
            public void onCreateFailure(String s) {
                Log.d("OFFER", s);
            }
        }, mediaConstraints);
    }

    private void doAnswer()
    {
        db.document("users/" + friendUid).update("call", userUid);

        db.document("users/" + userUid).get().addOnCompleteListener(new OnCompleteListener<DocumentSnapshot>() {
            @Override
            public void onComplete(@NonNull Task<DocumentSnapshot> task) {
                if(task.isSuccessful()  &&  task.getResult() != null)
                {
                    Map sdpData = (Map) task.getResult().get("sdp");
                    MediaConstraints mediaConstraints = new MediaConstraints();
                    mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"));
                    mediaConstraints.mandatory.add(new MediaConstraints.KeyValuePair("OfferToReceiveVideo", "true"));
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.OFFER, (String) sdpData.get("desc")));
                    peerConnection.createAnswer(new SimpleSdpObserver(){
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            peerConnection.setLocalDescription(new SimpleSdpObserver(), sessionDescription);
                            Map<String, String> sdp = new HashMap<>();
                            sdp.put("type", "answer");
                            sdp.put("desc", sessionDescription.description);
                            db.document("users/" + friendUid).update("sdp", sdp);
                        }
                    }, mediaConstraints);
                }
            }
        });
    }

    private void hangup()
    {
        db.document("users/" + friendUid).update("call", "hangup", "ice", null, "sdp", null);
        db.document("users/" + userUid).update("call", "hangup", "ice", null, "sdp", null);
    }

    private void setupFireStoreListeners()
    {
        //listen for ice candidates
        db.document("users/" + userUid).addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if(value != null  &&  value.get("ice") != null)
                {
                    List<Map> iceList = (List<Map>) value.get("ice");
                    if(iceList == null) iceList = new ArrayList<>();
                    for(Map iceCandidate : iceList) {
                        Log.d("WEBRTCD", "Ice added");
                        peerConnection.addIceCandidate(new IceCandidate((String) iceCandidate.get("id"), Integer.parseInt(iceCandidate.get("label") + ""), (String) iceCandidate.get("sdp")));
                    }
                    //db.document("users/" + userUid).update("ice", null);
                }
            }
        });

        //listen for hangup
        db.document("users/" + userUid).addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if(value != null  &&  value.get("call") != null  &&  value.get("call").equals("hangup"))
                {
                    db.document("users/" + userUid).update("call", null);
                    endCall();
                }
            }
        });

        //listen for answer if initiator
        if(!isInitiator) return;
        db.document("users/" + userUid).addSnapshotListener(this, new EventListener<DocumentSnapshot>() {
            @Override
            public void onEvent(@Nullable DocumentSnapshot value, @Nullable FirebaseFirestoreException error) {
                if(value != null  &&  value.get("sdp") != null) {
                    peerConnection.setRemoteDescription(new SimpleSdpObserver(), new SessionDescription(SessionDescription.Type.ANSWER, (String) ((Map) value.get("sdp")).get("desc")));
                    db.document("users/" + userUid).update("sdp", null);
                }
            }
        });
    }

    private void endCall(){
        peerConnection.close();
        super.onBackPressed();
    }

    private VideoCapturer createVideoCapturer() {
        VideoCapturer videoCapturer;

        CameraEnumerator enumerator;
        if(Camera2Enumerator.isSupported(this))
            enumerator = new Camera2Enumerator(this);
        else
            enumerator = new Camera1Enumerator(true);

        for (String device : enumerator.getDeviceNames()) {
            if(enumerator.isFrontFacing(device)) {
                videoCapturer = enumerator.createCapturer(device, null);
                if(videoCapturer != null)
                    return videoCapturer;
            }
        }

        for (String device : enumerator.getDeviceNames()) {
            if(!enumerator.isFrontFacing(device)) {
                videoCapturer = enumerator.createCapturer(device, null);
                if(videoCapturer != null)
                    return videoCapturer;
            }
        }
        return null;
    }

    private void checkPermissions() {
        if(Build.VERSION.SDK_INT >= 23)
        {
            if(ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_DENIED  ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.MODIFY_AUDIO_SETTINGS) == PackageManager.PERMISSION_DENIED  ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_DENIED)
                requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO, Manifest.permission.MODIFY_AUDIO_SETTINGS}, CAMERA_PERMISSION_CODE);
        }
    }
}