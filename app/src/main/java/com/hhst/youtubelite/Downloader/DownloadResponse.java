package com.hhst.youtubelite.Downloader;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import com.github.kiulian.downloader.downloader.response.Response;


import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class DownloadResponse {

    private final Response<File> videoResponse;
    private final Response<File> audioResponse;
    private final File outputDir;
    private final Context context;
    private final AtomicInteger state;

    private File videoFile;
    private File audioFile;
    private File output;

    public static final int INITIALIZED = 0;
    public static final int DOWNLOADING = 1;
    public static final int MUXING = 2;
    public static final int COMPLETED = 3;

    public DownloadResponse(
            Context context,
            Response<File> videoResponse,
            Response<File> audioResponse,
            File outputDir
    ) {
        this.context = context;
        this.videoResponse = videoResponse;
        this.audioResponse = audioResponse;
        this.outputDir = outputDir;
        state = new AtomicInteger(INITIALIZED);
    }

    public void execute(DownloadFinishCallback onFinish) {

        state.set(DOWNLOADING);
        audioFile = audioResponse.data();
        if (videoResponse != null) {
            videoFile = videoResponse.data();
            output = new File(outputDir, videoFile.getName());
        } else {
            output = new File(outputDir, audioFile.getName());
        }

        try {
            if (videoResponse != null) {
                state.set(MUXING);
            }
            if (onFinish != null) {
                onFinish.apply(videoFile, audioFile, output);
            }
            // notify system media library
            MediaScannerConnection.scanFile(context, new String[]{output.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Log.e("after download", Log.getStackTraceString(e));
        } finally {
            state.set(COMPLETED);
        }

    }


    public boolean cancel() {
        boolean audioCanceled = audioResponse.cancel();
        boolean videoCanceled = false;
        if (videoResponse != null) {
            videoCanceled = videoResponse.cancel();
        }
        return audioCanceled || videoCanceled;
    }

    public int getState() {
        return state.get();
    }

    public List<File> getCache() {
        return Arrays.asList(audioFile, videoFile);
    }

    public File getOutput() {
        return output;
    }

}
