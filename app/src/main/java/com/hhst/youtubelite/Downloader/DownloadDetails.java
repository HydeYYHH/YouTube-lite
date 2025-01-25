package com.hhst.youtubelite.Downloader;

import androidx.annotation.NonNull;

import com.github.kiulian.downloader.model.videos.formats.AudioFormat;
import com.github.kiulian.downloader.model.videos.formats.VideoFormat;

import java.util.List;
import java.util.Locale;

public class DownloadDetails {

    String title;
    String author;
    String thumbnail;
    List<String> thumbnails;
    List<VideoFormat> videoFormats;
    List<AudioFormat> audioFormats;

    public String getAuthor() {
        return author;
    }

    public String getTitle() {
        return title;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public List<String> getThumbnails() {
        return thumbnails;
    }

    public List<VideoFormat> getVideoFormats() {
        return videoFormats;
    }

    public List<AudioFormat> getAudioFormats() {
        return audioFormats;
    }

    @NonNull
    @Override
    public String toString() {
        return String.format(Locale.getDefault(), "author: %s, title: %s, thumbnail url: %s", author, title, thumbnail);
    }
}
