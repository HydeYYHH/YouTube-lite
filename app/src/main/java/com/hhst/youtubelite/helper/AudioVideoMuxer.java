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
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioVideoMuxer {

    private final AtomicBoolean canceled = new AtomicBoolean(false);

    public void cancel() {
        canceled.set(true);
    }

    @SuppressLint("WrongConstant")
    public void mux(File videoFile, File audioFile, File outputFile) throws IOException, InterruptedException {

        try {
            // for test
            long point = System.currentTimeMillis();

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
            MediaMuxer muxer = new MediaMuxer(outputFile.getAbsolutePath(),
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            int videoTrackIndex = -1;
            int audioTrackIndex = -1;

            Log.d("test time cost 1", String.valueOf(System.currentTimeMillis() - point));
            point = System.currentTimeMillis();

            // add video track
            for (int i = 0; i < videoExtractor.getTrackCount(); ++i) {
                if (canceled.get()) {
                    break;
                }
                MediaFormat format = videoExtractor.getTrackFormat(i);
                String mimeType = format.getString(MediaFormat.KEY_MIME);
                if (mimeType != null && mimeType.startsWith("video/")) {
                    videoExtractor.selectTrack(i);
                    videoTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            Log.d("test time cost 2", String.valueOf(System.currentTimeMillis() - point));
            point = System.currentTimeMillis();

            // add audio track
            for (int i = 0; i < audioExtractor.getTrackCount(); ++i) {
                if (canceled.get()) {
                    break;
                }
                MediaFormat format = audioExtractor.getTrackFormat(i);
                String mimeType = format.getString(MediaFormat.KEY_MIME);
                if (mimeType != null && mimeType.startsWith("audio/")) {
                    audioExtractor.selectTrack(i);
                    audioTrackIndex = muxer.addTrack(format);
                    break;
                }
            }

            Log.d("test time cost 3", String.valueOf(System.currentTimeMillis() - point));
            point = System.currentTimeMillis();

            // start to mux
            muxer.start();

            // Buffer and MediaCodec info
            ByteBuffer video_buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
            ByteBuffer audio_buffer = ByteBuffer.allocateDirect(2 * 1024 * 1024);
            MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();

            // Use threads for video and audio muxing
            int finalVideoTrackIndex = videoTrackIndex;
            Thread videoThread = new Thread(() -> {
                try {
                    int videoSampleSize;
                    while ((videoSampleSize = videoExtractor.readSampleData(video_buffer, 0)) > 0 &&
                            !canceled.get()) {
                        bufferInfo.flags = videoExtractor.getSampleFlags();
                        bufferInfo.offset = 0;
                        bufferInfo.size = videoSampleSize;
                        bufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                        muxer.writeSampleData(finalVideoTrackIndex, video_buffer, bufferInfo);
                        videoExtractor.advance();
                    }
                } catch (Exception e) {
                    Log.e("Video Thread Error", Log.getStackTraceString(e));
                }
            });

            int finalAudioTrackIndex = audioTrackIndex;
            Thread audioThread = new Thread(() -> {
                try {
                    int audioSampleSize;
                    while ((audioSampleSize = audioExtractor.readSampleData(audio_buffer, 0)) > 0 &&
                            !canceled.get()) {
                        audioBufferInfo.flags = audioExtractor.getSampleFlags();
                        audioBufferInfo.offset = 0;
                        audioBufferInfo.size = audioSampleSize;
                        audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
                        muxer.writeSampleData(finalAudioTrackIndex, audio_buffer, audioBufferInfo);
                        audioExtractor.advance();
                    }
                } catch (Exception e) {
                    Log.e("Audio Thread Error", Log.getStackTraceString(e));
                }
            });

            // Start both threads
            videoThread.start();
            audioThread.start();

            // Wait for both threads to finish
            videoThread.join();
            audioThread.join();

            Log.d("test time cost 4", String.valueOf(System.currentTimeMillis() - point));

            // release resources
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
