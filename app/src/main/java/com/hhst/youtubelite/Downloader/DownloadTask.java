package com.hhst.youtubelite.Downloader;


import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;
import com.hhst.youtubelite.helper.AudioVideoMuxer;

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;


public class DownloadTask {
    VideoFormat videoFormat;
    AudioFormat audioFormat;
    String fileName;
    DownloadNotification notification;
    DownloadResponse response;
    AtomicBoolean isRunning = new AtomicBoolean(true);
    String thumbnail;
    AudioVideoMuxer muxer;
    boolean isAudio;
    File outputDir;


    public DownloadTask(
            VideoFormat videoFormat,
            AudioFormat audioFormat,
            String thumbnail,
            String fileName,
            boolean isAudio,
            File outputDir
            ) {
        this.videoFormat = videoFormat;
        this.audioFormat = audioFormat;
        this.thumbnail = thumbnail;
        this.fileName = fileName;
        this.isAudio = isAudio;
        this.outputDir = outputDir;
    }

    public void setNotification(DownloadNotification notification) {
        this.notification = notification;
    }

    public DownloadNotification getNotification() {
        return notification;
    }

    public void setResponse(DownloadResponse response) {
        this.response = response;
    }

    public DownloadResponse getResponse() {
        return response;
    }

    public void setRunning(boolean isRunning) {
        this.isRunning.set(isRunning);
    }

    public void setMuxer(AudioVideoMuxer muxer) {
        this.muxer = muxer;
    }

    public AudioVideoMuxer getMuxer() {
        return muxer;
    }


    public File getOutput() {
        return outputDir;
    }

}
