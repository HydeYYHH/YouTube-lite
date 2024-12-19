package com.hhst.youtubelite.helper.muxer;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

public class AudioVideoMuxer {

    private final AtomicBoolean canceled = new AtomicBoolean(false);

    public void cancel() {
        canceled.set(true);
    }

    @SuppressLint("WrongConstant")
    public void mux(File videoFile, File audioFile, File outputFile) throws IOException {

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

            // for video
            List<Thread> video_threads = process(muxer, videoExtractor, videoTrackIndex);
            // for audio
            List<Thread> audio_thread = process(muxer, audioExtractor, audioTrackIndex);

            // Wait for all threads to finish
            video_threads.forEach(it -> {
                try {
                    it.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
            audio_thread.forEach(it -> {
                try {
                    it.join();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            Log.d("test time cost 4", String.valueOf(System.currentTimeMillis() - point));

            // release resources
            videoExtractor.release();
            audioExtractor.release();
            muxer.stop();
            muxer.release();

        } catch (Exception e) {
            Log.e("muxer error", Log.getStackTraceString(e));
            throw e;
        }
    }

    @SuppressLint("WrongConstant")
    private List<Thread> process(MediaMuxer muxer, MediaExtractor extractor, int trackIndex) {

        BlockingQueue<Sample> queue = new LinkedBlockingQueue<>();
        BufferPool bufferPool = new BufferPool(1024 * 1024, 20);
        AtomicBoolean done = new AtomicBoolean(false);

        // for speed up
        // read thread
        Thread readThread = new Thread(() -> {
            try {
                ByteBuffer buffer = ByteBuffer.allocate(1024 * 1024);
                while (!canceled.get()) {
                    int sampleSize = extractor.readSampleData(buffer, 0);
                    if (sampleSize > 0) {
                        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
                        bufferInfo.flags = extractor.getSampleFlags();
                        bufferInfo.offset = 0;
                        bufferInfo.size = sampleSize;
                        bufferInfo.presentationTimeUs = extractor.getSampleTime();
                        queue.put(new Sample(buffer, bufferInfo));
                        buffer = bufferPool.acquire();
                        extractor.advance();
                    } else {
                        done.set(true); // mark read process has finished
                        break;
                    }
                }
            } catch (Exception e) {
                Log.e("Muxer Process Error", Log.getStackTraceString(e));
                Thread.currentThread().interrupt();
            }
        });

        // write thread
        Thread writeThread = new Thread(() -> {
            try {
                while (!canceled.get()) {
                    if (done.get() && queue.isEmpty()) {
                        break;
                    }
                    Sample sample = queue.take();
                    ByteBuffer buffer = sample.buffer;
                    muxer.writeSampleData(trackIndex, buffer, sample.info);
                    // release buffer to buffer pool
                    bufferPool.release(buffer);
                }
            } catch (InterruptedException e) {
                Log.e("Muxer Process Error", Log.getStackTraceString(e));
                Thread.currentThread().interrupt();
            }
        });

        readThread.start();
        writeThread.start();

        return List.of(readThread, writeThread);
    }


}
