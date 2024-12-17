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

        Log.d("get video id", String.valueOf(video_id));

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
                .maxRetries(5);
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
            File tempDir,
            File outputDir
    ) {
        YoutubeDownloader downloader = new YoutubeDownloader();

        long audioSize = audioFormat == null ? 0 : audioFormat.contentLength();
        long videoSize = videoFormat == null ? 0 : videoFormat.contentLength();
        float audioWeight = (float) audioSize / (audioSize + videoSize);
        float videoWeight = (float) videoSize / (audioSize + videoSize);

        AtomicInteger audioProgress = new AtomicInteger(0);
        AtomicInteger videoProgress = new AtomicInteger(0);

        Response<File> audioResponse = null;
        File audioFile = null;
        if (audioFormat != null) {
            RequestVideoFileDownload audioRequest = new RequestVideoFileDownload(audioFormat)
                    .callback(new YoutubeProgressCallback<File>() {
                        @Override
                        public void onDownloading(int progress) {
                            audioProgress.set(progress);
                            callback.onDownloading((int)(videoProgress.get() * videoWeight + progress * audioWeight));
                            if (progress == 100 && videoProgress.get() == 100) {
                                callback.onDownloading(100);
                            }
                        }

                        @Override
                        public void onFinished(File data) {
                            // move output to cache directory
                            boolean ignored = data.renameTo(new File(context.getCacheDir(), data.getName()));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            callback.onError(throwable);
                        }
                    })
                    .renameTo(fileName)
                    .saveTo(context.getCacheDir())
                    .maxRetries(5)
                    .overwriteIfExists(true)
                    .async();

            if (!(audioFile = audioRequest.getOutputFile()).exists()) {
                // save to temp directory first
                audioRequest = audioRequest.saveTo(tempDir);
                audioResponse = downloader.downloadVideoFile(audioRequest);
            } else {
                audioProgress.set(100);
            }
        }

        Response<File> videoResponse = null;
        File videoFile = null;
        if (videoFormat != null) {
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
                            // move output to cache directory
                            boolean ignored = data.renameTo(new File(context.getCacheDir(), data.getName()));
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            callback.onError(throwable);
                        }
                    })
                    .renameTo(fileName)
                    .saveTo(context.getCacheDir())
                    .maxRetries(5)
                    .overwriteIfExists(true)
                    .async();

            if (!(videoFile = videoRequest.getOutputFile()).exists()) {
                videoRequest.saveTo(tempDir);
                videoResponse = downloader.downloadVideoFile(videoRequest);
            } else {
                videoProgress.set(100);
            }
        }

        return new DownloadResponse(
                context,
                videoResponse,
                audioResponse,
                audioFile,
                videoFile,
                new File(outputDir, videoFile == null ? Objects.requireNonNull(audioFile).getName() :
                        videoFile.getName())
        );
    }

}
