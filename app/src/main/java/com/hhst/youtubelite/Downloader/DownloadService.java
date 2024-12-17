package com.hhst.youtubelite.Downloader;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Binder;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.github.kiulian.downloader.downloader.YoutubeProgressCallback;
import com.hhst.youtubelite.R;
import com.hhst.youtubelite.helper.AudioVideoMuxer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

public class DownloadService extends Service {

    public final int max_download_tasks = 5;
    private HashMap<Integer, DownloadTask> download_tasks;
    private ExecutorService download_executor;

    @Override
    public void onCreate() {
        super.onCreate();
        download_tasks = new HashMap<>();
        download_executor = Executors.newFixedThreadPool(max_download_tasks);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent.getAction();
        int taskId = intent.getIntExtra("taskId", -1);
        if ("CANCEL_DOWNLOAD".equals(action)) {
            cancelDownload(taskId);
        } else if ("RETRY_DOWNLOAD".equals(action)) {
            retryDownload(taskId);
        } else if ("DELETE_DOWNLOAD".equals(action)) {
            deleteDownload(taskId);
        }
        return super.onStartCommand(intent, flags, startId);
    }

    public class DownloadBinder extends Binder {

        public DownloadService getService() {
            return DownloadService.this;
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new DownloadBinder();
    }

    private String sanitizeFileName(String fileName) {
        Pattern INVALID_FILENAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
        return INVALID_FILENAME_PATTERN.matcher(fileName).replaceAll("_");
    }

    private InputStream downloadFromURL(URL url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setDoInput(true);
        connection.connect();
        return connection.getInputStream();
    }

    private void showToast(String content) {
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                this,
                content,
                Toast.LENGTH_SHORT
        ).show());
    }

    private void downloadThumbnail(String thumbnail, File outputFile) {
        if (thumbnail != null) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                InputStream input = null;
                OutputStream output = null;
                try {
                    input = downloadFromURL(new URL(thumbnail));
                    // convert to bitmap
                    Bitmap bitmap = BitmapFactory.decodeStream(input);

                    output = Files.newOutputStream(outputFile.toPath());
                    // output as JPEG
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                    output.flush();

                    // notify to scan
                    MediaScannerConnection.scanFile(this, new String[]{outputFile.getAbsolutePath()}, null, null);
                    showToast(getString(R.string.thumbnail_has_been_saved_to) + outputFile);

                } catch (Exception e) {
                    Log.e("When download thumbnail", Log.getStackTraceString(e));
                    showToast(getString(R.string.failed_to_download_thumbnail) + e);
                }  finally {
                    try {
                        if (input != null) {
                            input.close();
                        }
                        if (output != null) {
                            output.close();
                        }
                    } catch (IOException e) {
                        Log.e("Stream close error", Log.getStackTraceString(e));
                    }
                }
            });
        }
    }

    private void scheduleDownload(DownloadTask task) {
        // Generate common properties
        String fileName = task.isAudio ? String.format("(audio only) %s", task.fileName)
                : String.format("%s-%s", task.fileName, task.videoFormat.qualityLabel());
        int taskId = download_tasks.size();
        DownloadNotification notification = new DownloadNotification(this, taskId);

        // Show notification
        notification.showNotification(fileName, 0);

        // Create progress callback
        YoutubeProgressCallback<File> callback = new YoutubeProgressCallback<File>() {
            @Override
            public void onDownloading(int progress) {
                if (task.isRunning.get()) {
                    notification.updateProgress(progress);
                }
            }

            @Override
            public void onFinished(File data) {
                // Handle completion
            }

            @Override
            public void onError(Throwable e) {
                Log.e("on downloading", Log.getStackTraceString(e));
                showToast(getString(R.string.failed_to_download) + e);
                task.setRunning(false);
                task.getNotification().cancelDownload(getString(R.string.failed_to_download));
            }
        };

        // Prepare download response
        DownloadResponse response = Downloader.download(
                this,
                task.isAudio ? null : task.videoFormat,
                task.audioFormat,
                callback,
                task.fileName,
                tempDir,
                task.getOutput()
        );

        // Submit task for execution
        download_executor.submit(() -> response.execute((video, audio, output) -> {
            // on download finished
            if (task.isAudio) {
                // Move audio file
                try {
                    copyFile(audio, output);
                } catch (IOException e) {
                    Log.e("Error copying audio file", Log.getStackTraceString(e));
                }

                notification.completeDownload(
                        String.format(getString(R.string.download_finished), fileName, output.getPath()),
                        output,
                        "audio/*"
                );
            } else {
                notification.afterDownload();
                try {
                    AudioVideoMuxer muxer = new AudioVideoMuxer();
                    task.setMuxer(muxer);
                    File temp_output = new File(tempDir, output.getName());
                    muxer.mux(video, audio, temp_output);
                    copyFile(temp_output, output);
                    boolean ignored = temp_output.delete();
                } catch (IOException | InterruptedException e) {
                    notification.cancelDownload(getString(R.string.merge_error));
                    showToast(getString(R.string.merge_error));
                    return;
                }

                notification.completeDownload(
                        String.format(getString(R.string.download_finished), fileName, output.getPath()),
                        output,
                        "video/*"
                );
            }

            showToast(String.format(getString(R.string.download_finished), fileName, output.getPath()));
            task.setRunning(false);
        }));

        // Finalize task setup
        task.setResponse(response);
        task.setNotification(notification);
        download_tasks.put(taskId, task);
    }



    private void copyFile(File source, File destination) throws IOException {
        try (FileInputStream input = new FileInputStream(source);
             FileOutputStream output = new FileOutputStream(destination)) {
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }

    private File tempDir;

    public void initiateDownload(DownloadTask task) {
        // check and create output directory
        File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                getString(R.string.app_name));

        if (!outputDir.exists() && !outputDir.mkdir()) {
            return;
        }

        // check and create temp directory
        tempDir = new File(getCacheDir(), "temp");
        if (!tempDir.exists() && !tempDir.mkdir()) {
            return;
        }

        // replace illegal character in filename
        task.fileName = sanitizeFileName(task.fileName);

        // download thumbnail
        downloadThumbnail(task.thumbnail, new File(outputDir, task.fileName + ".jpg"));

        // download audio
        if (task.isAudio) {
            scheduleDownload(new DownloadTask(
                    null,
                    task.audioFormat,
                    null,
                    task.fileName,
                    true,
                    outputDir
            ));
        }
        // download video
        if (task.videoFormat != null) {
            scheduleDownload(new DownloadTask(
                    task.videoFormat,
                    task.audioFormat,
                    null,
                    task.fileName,
                    false,
                    outputDir
            ));
        }
    }


    // user click cancel button to trigger cancel task event
    private void cancelDownload(int taskId) {
        DownloadTask task = download_tasks.get(taskId);
        if (task == null) {
            return;
        }
        DownloadResponse response = Objects.requireNonNull(task).getResponse();

        if (response.getState() == DownloadResponse.DOWNLOADING) {
            if (response.cancel()) {
                showToast(getString(R.string.download_canceled));
            } else {
                // cancel error
                showToast(getString(R.string.download_canceled_err));
            }
        } else if (response.getState() == DownloadResponse.MUXING) {
            // if download has completed and is merging
            // try to cancel mux process
            task.getMuxer().cancel();
            showToast(getString(R.string.merging_canceled));
        }

        // remove output file
        removeOutput(response);

        // Set the running flag to false to stop the task
        // This will halt the progress updates and allow for the next notification handling
        task.setRunning(false);
        task.getNotification().cancelDownload("");
    }

    // user click retry button to trigger this
    private void retryDownload(int taskId) {
        DownloadTask task = download_tasks.get(taskId);
        if (task == null) {
            return;
        }
        DownloadResponse response = Objects.requireNonNull(task).getResponse();
        // try cancel original task first
        if (response.getState() == DownloadResponse.DOWNLOADING) {
            response.cancel();
        }else if (response.getState() == DownloadResponse.MUXING) {
            task.getMuxer().cancel();
        }
        showToast(getString(R.string.retry_download) + task.fileName);
        // remove output files
        removeOutput(response);

        // Set the running flag to false to stop the task
        // This will halt the progress updates and allow for the next notification handling
        task.setRunning(false);

        // dismiss the notification
        task.getNotification().clearDownload();

        if (task.isAudio) {
            // initiate new download task for the video
            initiateDownload(new DownloadTask(
                    null,
                    task.audioFormat,
                    null,
                    task.fileName,
                    true,
                    null
            ));
        } else {
            initiateDownload(new DownloadTask(
                    task.videoFormat,
                    task.audioFormat,
                    null,
                    task.fileName,
                    false,
                    null
            ));
        }

    }

    // delete download file after download has finished
    private void deleteDownload(int taskId) {
        DownloadTask task = download_tasks.get(taskId);
        DownloadResponse response = Objects.requireNonNull(task).getResponse();
        if (response.getState() == DownloadResponse.COMPLETED) {
            // only triggered this under the COMPLETED flag
            if (response.getOutput().delete()) {
                // dismiss the notification
                task.getNotification().clearDownload();
                // show toast
                showToast(getString(R.string.file_deleted));
            }
        }
    }

    private void removeOutput(DownloadResponse response) {
        File output = response.getOutput();
        if (output != null && !output.delete()) {
            output.deleteOnExit();
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        stopSelf();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (!download_tasks.isEmpty()) {
            Objects.requireNonNull(download_tasks.get(0)).getNotification().clearAll();

        }
        if (download_executor != null && !download_executor.isShutdown()) {
            download_executor.shutdownNow();
        }
        super.onDestroy();
    }

}