package com.hhst.youtubelite.helper.muxer;

import android.media.MediaCodec;

import java.nio.ByteBuffer;


public class Sample {
    final ByteBuffer buffer;
    final MediaCodec.BufferInfo info;

    Sample(ByteBuffer buffer, MediaCodec.BufferInfo info) {
        this.buffer = buffer;
        this.info = info;
    }

}
