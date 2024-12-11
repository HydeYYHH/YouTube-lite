package com.hhst.youtubelite.Downloader;

import android.app.Service;
import android.content.Context;
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

    class DownloadBinder extends Binder {

        DownloadService getService() {
            return DownloadService.this;
        }

    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return new DownloadBinder();
    }


    public void initiateDownload(DownloadTask task) {
        File outputDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                getString(R.string.app_name));

        if (!outputDir.exists() && !outputDir.mkdir()) {
            return;
        }

        // replace illegal character in filename
        Pattern INVALID_FILENAME_PATTERN = Pattern.compile("[\\\\/:*?\"<>|]");
        task.fileName = INVALID_FILENAME_PATTERN.matcher(task.fileName).replaceAll("_");

        // download thumbnail
        if (task.thumbnail != null) {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            executor.execute(() -> {
                InputStream input = null;
                OutputStream output = null;
                try {
                    URL url = new URL(task.thumbnail);
                    HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                    connection.setDoInput(true);
                    connection.connect();
                    input = connection.getInputStream();

                    // convert to bitmap
                    Bitmap bitmap = BitmapFactory.decodeStream(input);

                    File outputFile = new File(outputDir, task.fileName + ".jpg");
                    output = Files.newOutputStream(outputFile.toPath());
                    // output as JPEG
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, output);
                    output.flush();

                    // notify to scan
                    MediaScannerConnection.scanFile(this, new String[]{outputFile.getAbsolutePath()}, null, null);
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                            this,
                            getString(R.string.thumbnail_has_been_saved_to) + outputFile,
                            Toast.LENGTH_SHORT
                    ).show());

                } catch (Exception e) {
                    Log.e("When download thumbnail", Log.getStackTraceString(e));
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                            this,
                            getString(R.string.failed_to_download_thumbnail) + e,
                            Toast.LENGTH_SHORT
                    ).show());
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

        // download video
        if (task.videoFormat == null) {
            return;
        }
        // generate task id
        int taskId = download_tasks.size();
        Context context = this;
        DownloadNotification notification = new DownloadNotification(context, taskId);
        // show notification
        notification.showNotification(task.fileName, 0);

        // get download response
        DownloadResponse response = Downloader.download(
                    context,
                    task.videoFormat,
                    task.audioFormat,
                    new YoutubeProgressCallback<File>() {
                        @Override
                        public void onDownloading(int progress) {
                            notification.updateProgress(progress);
                        }

                        @Override
                        public void onFinished(File data) {

                        }

                        @Override
                        public void onError(Throwable throwable) {
                            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                                    context,
                                    getString(R.string.failed_to_download) + throwable,
                                    Toast.LENGTH_SHORT
                            ).show());
                            task.setRunning(false);
                            task.getNotification().cancelDownload(getString(R.string.failed_to_download));
                        }
                    },
                    task.fileName,
                    outputDir
            );

        // submit task and start to download video
        download_executor.submit(() -> response.execute(((video, audio, output) -> {
            // show notification for merging
            notification.afterDownload();
            try {
                // for audio and video merging
                AudioVideoMuxer muxer = new AudioVideoMuxer();
                task.setMuxer(muxer);
                muxer.mux(video, audio, output);
            } catch (IOException e) {
                notification.cancelDownload(getString(R.string.merge_error));
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                        context,
                        getString(R.string.merge_error),
                        Toast.LENGTH_SHORT
                ).show());
            }

            // update notification
            notification.completeDownload(
                    String.format(getString(R.string.download_finished), task.fileName, output.getPath()),
                    output,
                    "video/*"
            );
            // show a toast for user to notify download complete
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                    context,
                    String.format(getString(R.string.download_finished),
                            task.fileName, output.getPath()),
                    Toast.LENGTH_SHORT
            ).show());

            task.setRunning(false);
        })));
        task.setResponse(response);
        task.setNotification(notification);
        // add task to list for future reference
        download_tasks.put(taskId, task);
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
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                        this,
                        getString(R.string.download_canceled),
                        Toast.LENGTH_SHORT
                ).show());
            } else {
                // cancel error
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                        this,
                        R.string.download_canceled_err_because_has_already_completed_normally_or_has_already_finished_with_an_error,
                        Toast.LENGTH_SHORT
                ).show());
            }
        } else if (response.getState() == DownloadResponse.MUXING) {
            // if download has completed and is merging
            for (File cacheFile : response.getCache()) {
                if (!cacheFile.delete()) {
                    cacheFile.deleteOnExit();
                }
                task.getMuxer().cancel();
                if (!response.getOutput().delete()) {
                    response.getOutput().deleteOnExit();
                }
            }
            new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                    this,
                    R.string.merging_canceled,
                    Toast.LENGTH_SHORT
            ).show());
        }

        task.getNotification().cancelDownload("");

        task.setRunning(false);

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
            for (File cacheFile : response.getCache()) {
                if (!cacheFile.delete()) {
                    cacheFile.deleteOnExit();
                }
                task.getMuxer().cancel();
                if (!response.getOutput().delete()) {
                    response.getOutput().deleteOnExit();
                }
            }
        }
        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                this,
                getString(R.string.retry_download) + task.fileName,
                Toast.LENGTH_SHORT
        ).show());
        task.getNotification().clearDownload();
        task.setRunning(false);
        task.setThumbnail(null);
        initiateDownload(task);
    }

    // delete download file after download has finished
    private void deleteDownload(int taskId) {
        DownloadTask task = download_tasks.get(taskId);
        DownloadResponse response = Objects.requireNonNull(task).getResponse();
        if (response.getState() == DownloadResponse.COMPLETED) {
            if (response.getOutput().delete()) {
                task.getNotification().clearDownload();
                new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(
                        this,
                        R.string.file_deleted,
                        Toast.LENGTH_SHORT
                ).show());
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (download_executor != null && !download_executor.isShutdown()) {
            download_executor.shutdownNow();
        }
    }


}
