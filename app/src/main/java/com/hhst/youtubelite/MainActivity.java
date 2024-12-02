package com.hhst.youtubelite;

import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ProgressBar;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

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
                        value -> {}
                )
        );
        swipeRefreshLayout.setProgressViewOffset(true, 80,180);

        loadScript();
        webview.build();
        webview.loadUrl(getString(R.string.base_url));

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
                    value -> {}
            );
            if (webview.fullscreen != null && webview.fullscreen.getVisibility() == View.VISIBLE){
                webview.evaluateJavascript(
                        "document.exitFullscreen()",
                        value -> {}
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

}