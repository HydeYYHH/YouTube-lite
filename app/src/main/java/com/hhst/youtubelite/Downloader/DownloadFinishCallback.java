package com.hhst.youtubelite.Downloader;

import java.io.File;
import java.io.IOException;

public interface DownloadFinishCallback {

    void apply(File video, File audio, File output) throws IOException;

}
