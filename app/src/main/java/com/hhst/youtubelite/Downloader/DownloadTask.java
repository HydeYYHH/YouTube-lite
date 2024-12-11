package com.hhst.youtubelite.Downloader;


import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;
import com.hhst.youtubelite.helper.AudioVideoMuxer;

public class DownloadTask {
    VideoFormat videoFormat;
    AudioFormat audioFormat;
    String fileName;
    DownloadResponse response;
    DownloadNotification notification;
    boolean isRunning = true;
    String thumbnail;
    AudioVideoMuxer muxer;


    public DownloadTask(
            VideoFormat videoFormat,
            AudioFormat audioFormat,
            String thumbnail,
            String fileName) {
        this.videoFormat = videoFormat;
        this.audioFormat = audioFormat;
        this.thumbnail = thumbnail;
        this.fileName = fileName;
    }

    public void setResponse(DownloadResponse response) {
        this.response = response;
    }

    public DownloadResponse getResponse() {
        return response;
    }

    public void setNotification(DownloadNotification notification) {
        this.notification = notification;
    }

    public DownloadNotification getNotification() {
        return notification;
    }

    public void setRunning(boolean running) {
        isRunning = running;
    }

    public boolean isRunning() {
        return isRunning;
    }

    public void setThumbnail(String thumbnail) {
        this.thumbnail = thumbnail;
    }

    public void setMuxer(AudioVideoMuxer muxer) {
        this.muxer = muxer;
    }

    public AudioVideoMuxer getMuxer() {
        return muxer;
    }

}
