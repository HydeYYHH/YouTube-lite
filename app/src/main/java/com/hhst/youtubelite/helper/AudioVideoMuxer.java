package com.hhst.youtubelite.helper;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class AudioVideoMuxer {

    @SuppressLint("WrongConstant")
    public void mux(File videoFile, File audioFile, File outputFile) throws IOException {

        try {
            // delete output file if it exists
            if (outputFile.exists() && !outputFile.delete()) {
                return;
            }
            // extractors
            MediaExtractor videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(videoFile.getAbsolutePath());
            MediaExtractor audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(audioFile.getAbsolutePath());

            // media muxer
            MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int videoTrackIndex = 0;
            int audioTrackIndex = 0;

            // add video track
            for (int i = 0; i < videoExtractor.getTrackCount(); ++i) {
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mimeType = format.getString(MediaFormat.KEY_MIME);
                if (mimeType != null && mimeType.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            // add audio track
            for (int i = 0; i < audioExtractor.getTrackCount(); ++i) {
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mimeType = format.getString(MediaFormat.KEY_MIME);
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            // start to mux
            muxer.start();
            ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            int videoSampleSize;

            while ((videoSampleSize = videoExtractor.readSampleData(buffer, 0)) > 0) {
                bufferInfo.flags = videoExtractor.getSampleFlags();
                bufferInfo.offset = 0;
                bufferInfo.size = videoSampleSize;
                bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();

                muxer.writeSampleData(videoTrackIndex, buffer, bufferInfo);

                videoExtractor.advance();
            }

            int audioSampleSize;
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

            while ((audioSampleSize = audioExtractor.readSampleData(buffer, 0)) > 0) {
                audioBufferInfo.flags = audioExtractor.getSampleFlags();
                audioBufferInfo.offset = 0;
                audioBufferInfo.size = audioSampleSize;
                audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();

                muxer.writeSampleData(audioTrackIndex, buffer, audioBufferInfo);

                audioExtractor.advance();
            }

            // release them
            videoExtractor.release();
            audioExtractor.release();
            muxer.stop();
            muxer.release();

        } catch (Exception e) {
            Log.e("muxer", Log.getStackTraceString(e));
            throw e;
        }

    }
}