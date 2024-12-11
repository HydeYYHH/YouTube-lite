package com.hhst.youtubelite.Downloader;

import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RadioGroup;
import android.widget.Toast;

import com.github.kiulian.downloader.model.videos.formats.VideoFormat;
import com.hhst.youtubelite.R;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class DownloadDialog {
    private final String video_id;
    private final Context context;
    private DownloadService downloadService;
    private boolean dBound;

    private DownloadDetails details = null;
    private View dialogView;



    public DownloadDialog(String video_id, Context context) {
        this.video_id = video_id;
        this.context = context;
        dBound = false;
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                downloadService = ((DownloadService.DownloadBinder) iBinder).getService();
                dBound = true;
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
                dBound = false;
            }
        };

        Intent intent = new Intent(context, DownloadService.class);
        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        if (details == null) {
            details = Downloader.info(video_id);
        }
    }

    public void show() {

        // bind download service

        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.download));

        dialogView = View.inflate(context, R.layout.download_dialog, null);
        builder.setView(dialogView);
        builder.setCancelable(true);
        AlertDialog dialog = builder.create();


        ImageView imageView = dialogView.findViewById(R.id.download_image);
        EditText editText = dialogView.findViewById(R.id.download_edit_text);
        Button buttonVideo = dialogView.findViewById(R.id.button_video);
        Button buttonThumbnail = dialogView.findViewById(R.id.button_thumbnail);
        Button buttonCancel = dialogView.findViewById(R.id.button_cancel);
        Button buttonDownload = dialogView.findViewById(R.id.button_download);

        // load image
        loadImage(imageView);

        // load default video name
        loadVideoName(editText);

        // state
        AtomicBoolean isVideoSelected = new AtomicBoolean(false);
        AtomicBoolean isThumbnailSelected = new AtomicBoolean(false);
        AtomicReference<VideoFormat> selectedQuality = new AtomicReference<>(null);

        // set button default background color
        buttonVideo.setBackgroundColor(context.getColor(android.R.color.darker_gray));
        buttonThumbnail.setBackgroundColor(context.getColor(android.R.color.darker_gray));

        // on video button clicked
        buttonVideo.setOnClickListener(v -> showVideoQualityDialog(selectedQuality, quality_selected -> {
            isVideoSelected.set(quality_selected);
            if (isVideoSelected.get()) {
                buttonVideo.setBackgroundColor(context.getColor(android.R.color.holo_blue_dark));
            } else {
                buttonVideo.setBackgroundColor(context.getColor(android.R.color.darker_gray));
            }
        }));

        // on thumbnail button clicked
        buttonThumbnail.setOnClickListener(v -> {
            isThumbnailSelected.set(!isThumbnailSelected.get());
            buttonThumbnail.setSelected(isThumbnailSelected.get());
            if (isThumbnailSelected.get()) {
                buttonThumbnail.setBackgroundColor(context.getColor(android.R.color.holo_blue_dark));
            } else {
                buttonThumbnail.setBackgroundColor(context.getColor(android.R.color.darker_gray));
            }
        });

        // on download button clicked
        buttonDownload.setOnClickListener(v -> {
            if (!isVideoSelected.get() && !isThumbnailSelected.get()) {
                dialogView.post(() -> Toast.makeText(
                        context,
                        R.string.select_something_first,
                        Toast.LENGTH_SHORT
                ).show());
                return;
            }

            String fileName = editText.getText().toString().trim();
            String thumbnail = isThumbnailSelected.get() ? details.thumbnail: null;
            downloadService.initiateDownload(new DownloadTask(
                    selectedQuality.get(), details.audioFormats.get(0), thumbnail, fileName
            ));
            dialog.dismiss();
        });


        buttonCancel.setOnClickListener(v -> dialog.dismiss());

        // show dialog
        dialog.show();

    }

    private void loadImage(ImageView imageView) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (details == null) {
                    details = Downloader.info(video_id);
                }
                URL url = new URL(details.getThumbnail());
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();

                Bitmap bitmap = BitmapFactory.decodeStream(input);

                dialogView.post(() -> imageView.setImageBitmap(bitmap));

            } catch (Exception e) {
                Log.e("When fetch thumbnail", Log.getStackTraceString(e));
                dialogView.post(() ->
                        Toast.makeText(context,
                                "Failed to load image:  " + e,
                                Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void loadVideoName(EditText editText) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (details == null) {
                    details = Downloader.info(video_id);
                }
                String title = details.getTitle();
                String author = details.getAuthor();
                String video_default_name = String.format("%s-%s", title, author);
                dialogView.post(() -> editText.setText(video_default_name));
            } catch (Exception e) {
                Log.e("When load video name", Log.getStackTraceString(e));
                dialogView.post(() -> Toast.makeText(
                        context, "Failed to load video details:  " + e,
                        Toast.LENGTH_SHORT).show()
                );
            }
        });
    }

    private void showVideoQualityDialog(
            AtomicReference<VideoFormat> selectedQuality,
            QualitySelectedListener listener
    ) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(context.getString(R.string.video_quality));

        View dialogView = View.inflate(context, R.layout.quality_selector, null);
        builder.setView(dialogView);

        LinearLayout quality_selector = dialogView.findViewById(R.id.quality_container);
        Button cancelButton = dialogView.findViewById(R.id.button_cancel);
        Button confirmButton = dialogView.findViewById(R.id.button_confirm);

        // create radio button dynamically
        // get video quality labels
        List<String> quality_labels = new ArrayList<>();
        // checked checkbox view, for mutex checkbox
        AtomicReference<CheckBox> checked_box = new AtomicReference<>();
        AtomicReference<VideoFormat> selected_format = new AtomicReference<>();
        // avoid trigger radioGroup.setOnCheckedChangeListener when initiate the radio button check state
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                if (details == null) {
                    details = Downloader.info(video_id);
                }
                details.getVideoFormats().forEach(it -> {
                    // avoid duplicate labels
                    if (!quality_labels.contains(it.qualityLabel())) {
                        quality_labels.add(it.qualityLabel());
                        CheckBox choice = new CheckBox(context);
                        choice.setText(it.qualityLabel());
                        choice.setLayoutParams(
                                new RadioGroup.LayoutParams(
                                        RadioGroup.LayoutParams.MATCH_PARENT,
                                        RadioGroup.LayoutParams.WRAP_CONTENT
                                )
                        );
                        choice.setOnCheckedChangeListener((v, isChecked) -> {
                            if (isChecked) {
                                if (checked_box.get() != null) {
                                    checked_box.get().setChecked(false);
                                }
                                selected_format.set(it);
                                checked_box.set((CheckBox) v);
                            } else {
                                selected_format.set(null);
                                checked_box.set(null);
                            }
                        });
                        quality_selector.addView(choice);
                        if (selectedQuality.get() != null && selectedQuality.get().equals(it)) {
                            choice.setChecked(true);
                        }
                    }
                });
            } catch (Exception e) {
                Log.e("When show VideoQualityDialog", Log.getStackTraceString(e));
                dialogView.post(() -> Toast.makeText(
                        context, "Failed to load available video quality:  " + e,
                        Toast.LENGTH_SHORT).show()
                );
            }
        });

        AlertDialog qualityDialog = builder.create();

        cancelButton.setOnClickListener(v -> qualityDialog.dismiss());
        confirmButton.setOnClickListener(v -> {
            if (checked_box.get() == null) {
                selectedQuality.set(null);
                listener.onQualitySelected(false);
            } else {
                selectedQuality.set(selected_format.get());
                listener.onQualitySelected(true);
            }
            qualityDialog.dismiss();
        });


        qualityDialog.show();
    }


    interface QualitySelectedListener {
        void onQualitySelected(boolean quality_selected);
    }



}
