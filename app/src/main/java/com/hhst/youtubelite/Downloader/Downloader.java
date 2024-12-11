package com.hhst.youtubelite.Downloader;

import android.content.Context;
import android.util.Log;

import com.github.kiulian.downloader.YoutubeDownloader;
import com.github.kiulian.downloader.downloader.YoutubeCallback;
import com.github.kiulian.downloader.downloader.YoutubeProgressCallback;
import com.github.kiulian.downloader.downloader.request.RequestVideoFileDownload;
import com.github.kiulian.downloader.downloader.request.RequestVideoInfo;
import com.github.kiulian.downloader.downloader.response.Response;
import com.github.kiulian.downloader.model.videos.VideoDetails;
import com.github.kiulian.downloader.model.videos.VideoInfo;
import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

public class Downloader {

    public static DownloadDetails info(String video_id) {

        RequestVideoInfo requestVideoInfo = new RequestVideoInfo(video_id)
                .callback(new YoutubeCallback<VideoInfo>() {
                    @Override
                    public void onFinished(VideoInfo videoInfo) {
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        Log.e("when get info", "Error: " + throwable.getMessage());
                    }
                })
                .maxRetries(3);
        Response<VideoInfo> response = new YoutubeDownloader().getVideoInfo(requestVideoInfo);
        VideoInfo videoInfo = response.data();

        VideoDetails videoDetails = Objects.requireNonNull(videoInfo).details();
        DownloadDetails details = new DownloadDetails();
        details.title = videoDetails.title();
        details.author = videoDetails.author();
        List<String> thumbnails = videoDetails.thumbnails();
        details.thumbnails = thumbnails;
        if (!thumbnails.isEmpty()) {
            details.thumbnail = thumbnails.get(thumbnails.size() - 1);
        } else {
            details.thumbnail = null;
        }
        details.videoFormats = videoInfo.videoFormats();
        details.videoFormats.removeAll(videoInfo.videoWithAudioFormats());

        // only use best audio format
        details.audioFormats = List.of(videoInfo.bestAudioFormat());

        return details;

    }

    public static DownloadResponse download(
            Context context,
            VideoFormat videoFormat,
            AudioFormat audioFormat,
            YoutubeProgressCallback<File> callback,
            String fileName,
            File outputDir
    ) {
        YoutubeDownloader downloader = new YoutubeDownloader();

        long audioSize = audioFormat.contentLength();
        long videoSize = videoFormat.contentLength();
        float audioWeight = (float) audioSize / (audioSize + videoSize);
        float videoWeight = (float) videoSize / (audioSize + videoSize);

        AtomicInteger audioProgress = new AtomicInteger(0);
        AtomicInteger videoProgress = new AtomicInteger(0);

        RequestVideoFileDownload audioRequest = new RequestVideoFileDownload(audioFormat)
                .callback(new YoutubeProgressCallback<File>() {
                    @Override
                    public void onDownloading(int progress) {
                        audioProgress.set(progress);
                        callback.onDownloading((int)(videoProgress.get() * videoWeight + progress * audioWeight));
                    }

                    @Override
                    public void onFinished(File data) {

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callback.onError(throwable);
                    }
                })
                .renameTo(fileName)
                .saveTo(context.getCacheDir())
                .overwriteIfExists(true)
                .maxRetries(5)
                .async();

        Response<File> audioResponse = downloader.downloadVideoFile(audioRequest);

        RequestVideoFileDownload videoRequest = new RequestVideoFileDownload(videoFormat)
                .callback(new YoutubeProgressCallback<File>() {
                    @Override
                    public void onDownloading(int progress) {
                        videoProgress.set(progress);
                        callback.onDownloading((int)(progress * videoWeight + audioProgress.get() * audioWeight));
                        if (progress == 100 && audioProgress.get() == 100) {
                            callback.onDownloading(100);
                        }
                    }

                    @Override
                    public void onFinished(File data) {

                    }

                    @Override
                    public void onError(Throwable throwable) {
                        callback.onError(throwable);
                    }
                })
                .renameTo(fileName)
                .saveTo(context.getCacheDir())
                .overwriteIfExists(true)
                .maxRetries(5)
                .async();

        Response<File> videoResponse = downloader.downloadVideoFile(videoRequest);

        return new DownloadResponse(context, videoResponse, audioResponse, outputDir);
    }

}
