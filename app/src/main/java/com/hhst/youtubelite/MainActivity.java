package com.hhst.youtubelite;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;


import com.hhst.youtubelite.Downloader.DownloadService;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    public YWebview webview;
    public SwipeRefreshLayout swipeRefreshLayout;
    public ProgressBar progressBar;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);
        webview = findViewById(R.id.webview);

        swipeRefreshLayout.setColorSchemeResources(R.color.light_blue, R.color.blue, R.color.dark_blue);
        swipeRefreshLayout.setOnRefreshListener(
                () -> webview.evaluateJavascript(
                        "window.dispatchEvent(new Event('onRefresh'));",
                        null
                )
        );
        swipeRefreshLayout.setProgressViewOffset(true, 80,180);

        loadScript();
        webview.build();
        webview.loadUrl(getString(R.string.base_url));

        requestPermissions();

        clearCache();
        startDownloadService();
    }

    public final int REQUEST_NOTIFICATION_CODE = 1;

    public void requestPermissions() {

        // check and require post-notification permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQUEST_NOTIFICATION_CODE
                );
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                        REQUEST_NOTIFICATION_CODE
                );
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {

                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        REQUEST_NOTIFICATION_CODE
                );
            }

        }
    }

    public void loadScript(){
        AssetManager assetManager = getAssets();

        List<String> res_pths = Arrays.asList("css", "js");
        try{
            for (String dir_path : res_pths) {
                ArrayList<String> resources = new ArrayList<>(
                        Arrays.asList(Objects.requireNonNull(assetManager.list(dir_path)))
                );
                String init_res = resources.contains("init.js") ? "init.js" :
                        resources.contains("init.min.js") ? "init.min.js" : null;
                if (init_res != null){
                    webview.injectJavaScript(
                            assetManager.open(dir_path + File.separator + init_res));
                    resources.remove(init_res);
                }
                for (String res : resources) {
                    InputStream stream = assetManager.open(dir_path + File.separator
                            + res);
                    if (res.endsWith(".js")) {
                        webview.injectJavaScript(stream);
                    } else if (res.endsWith(".css")) {
                        webview.injectCSS(stream);
                    }
                }
            }
        }catch (IOException e){
            Log.e("IOException", e.toString());
        }

    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent KeyEvent) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            webview.evaluateJavascript(
                    "window.dispatchEvent(new Event('onGoBack'));",
                    null
            );
            if (webview.fullscreen != null && webview.fullscreen.getVisibility() == View.VISIBLE){
                webview.evaluateJavascript(
                        "document.exitFullscreen()",
                        null
                );
                return true;
            }
            if (webview.canGoBack()){
                webview.goBack();
            } else {
                finish();
            }
            return true;
        }
        return false;
    }

    public void clearCache() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            File[] cacheDirs = {getCacheDir(), new File(getCacheDir(), "temp")};
            for (File cacheDir : cacheDirs) {
                if (cacheDir.exists() && cacheDir.isDirectory()) {
                    File[] files = cacheDir.listFiles(file -> file.isFile() &&
                            (file.getName().endsWith(".mp4") || file.getName().endsWith(".m4a"))
                    );
                    if (files != null) {
                        for (File file : files) {
                            boolean deleted = file.delete();
                            if (!deleted) {
                                Log.w("ClearCache", "Failed to delete file: " +
                                        file.getAbsolutePath());
                            }
                        }
                    }
                }
            }
        });
    }


    public DownloadService downloadService;

    public void startDownloadService() {
        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                downloadService = ((DownloadService.DownloadBinder) iBinder).getService();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };

        Intent intent = new Intent(this, DownloadService.class);
        bindService(intent, connection, Context.BIND_AUTO_CREATE);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }
}