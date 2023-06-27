package com.webrtc.android.avcall.view;

import android.content.Context;
import android.util.AttributeSet;
import android.util.Log;

import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoFrame;

public class SurfaceViewRenderer2 extends SurfaceViewRenderer {

    public SurfaceViewRenderer2(Context context) {
        super(context);
    }

    public SurfaceViewRenderer2(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void onFrame(VideoFrame frame) {
        super.onFrame(frame);
        VideoFrame.Buffer buffer = frame.getBuffer();
        int buf_height = buffer.getHeight();
        int buf_width  = buffer.getHeight();
        VideoFrame.I420Buffer buffer420 = buffer.toI420();
        buffer420.getDataU();
        buffer420.getDataV();
        buffer420.getDataY();
        long timestamp = frame.getTimestampNs() / 1000;
        Log.e("SurfaceViewRenderer2", "h:" + buf_height + " w:" + buf_width + " t:" + timestamp);
    }
};