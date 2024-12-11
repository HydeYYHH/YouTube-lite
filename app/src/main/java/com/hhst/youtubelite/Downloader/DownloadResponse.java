package com.hhst.youtubelite.Downloader;

import android.content.Context;
import android.media.MediaScannerConnection;
import android.util.Log;

import com.github.kiulian.downloader.downloader.response.Response;
import com.hhst.youtubelite.helper.AudioVideoMuxer;


import java.io.File;

public class DownloadResponse {

    private final Response<File> videoResponse;
    private final Response<File> audioResponse;
    private final File outputDir;
    private final Context context;

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
    }

    public void execute(DownloadFinishCallback onFinish) {

        File audio = audioResponse.data();
        File video = videoResponse.data();

        File output = new File(outputDir, video.getName());

        try {
            onFinish.apply(video, audio, output);
            // notify system media library
            MediaScannerConnection.scanFile(context, new String[]{output.getAbsolutePath()}, null, null);
        } catch (Exception e) {
            Log.e("after download", Log.getStackTraceString(e));
        } finally {
            // clear cache file
            if (!audio.delete()) {
                audio.deleteOnExit();
            }
            if (!video.delete()) {
                video.deleteOnExit();
            }
        }

    }


    public boolean cancel() {
        return audioResponse.cancel() && videoResponse.cancel();
    }

}
