package com.hhst.youtubelite;

import android.annotation.SuppressLint;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.net.Uri;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;


import com.hhst.youtubelite.helper.JavascriptInterface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

public class YWebview extends WebView {

    public ArrayList<String> js_res = new ArrayList<>();

    public View fullscreen = null;

    public List<String> allowed_domain = List.of(
            "youtube.com",
            "youtube.googleapis.com",
            "googlevideo.com",
            "ytimg.com",
            "accounts.google",
            "googleusercontent.com",
            "apis.google.com"
    );


    public YWebview(Context context) {
        super(context);
    }

    public YWebview(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public YWebview(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @SuppressLint("SetJavaScriptEnabled")
    public void build() {
        setLayerType(LAYER_TYPE_HARDWARE, null);

        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        setLayerType(LAYER_TYPE_HARDWARE, null);

        addJavascriptInterface(new JavascriptInterface(getContext()), "android");

        setWebViewClient(new WebViewClient() {

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (Objects.equals(request.getUrl().getScheme(), "intent")){
                    // open in other app
                    try {
                        Intent intent = Intent.parseUri(request.getUrl().toString(), Intent.URI_INTENT_SCHEME);
                        getContext().startActivity(intent);
                    } catch (ActivityNotFoundException | URISyntaxException e) {
                        post(() -> Toast.makeText(getContext(), "application not found", Toast.LENGTH_SHORT).show());
                        Log.e("IntentParsing", e.toString());
                    }
                } else {
                    // restrict domain
                    if (isAllowedDomain(request.getUrl())){
                        return false;
                    }
                    // open in browser
                    getContext().startActivity(new Intent(
                            Intent.ACTION_VIEW, request.getUrl()
                    ));
                }
                return true;
            }

            public boolean isAllowedDomain(Uri uri){
                String host = uri.getHost();
                if (host == null){
                    return false;
                }
                for (String domain: allowed_domain){
                    if (host.endsWith(domain) || host.startsWith(domain)){
                        return true;
                    }
                }
                return false;
            }

            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                super.doUpdateVisitedHistory(view, url, isReload);
                evaluateJavascript(
                        "window.dispatchEvent(new Event('doUpdateVisitedHistory'));",
                        value -> {}
                );
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                evaluateJavascript(
                        "window.dispatchEvent(new Event('onPageStarted'));",
                        value -> {}
                );
                for (String js : js_res) {
                    evaluateJavascript(js, value -> {});
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                evaluateJavascript(
                        "window.dispatchEvent(new Event('onPageFinished'));",
                        value -> {}
                );
                for (String js : js_res) {
                    evaluateJavascript(js, value -> {});
                }
            }

        });


        setWebChromeClient(new WebChromeClient() {

            @Override
            public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
                Log.d("js-log", consoleMessage.message() + " -- From line " +
                        consoleMessage.lineNumber() + " of " + consoleMessage.sourceId());
                return super.onConsoleMessage(consoleMessage);
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                ProgressBar progressBar = findViewById(R.id.progressBar);
                progressBar.setVisibility(VISIBLE);
                progressBar.setProgress(progress, true);
                if (progress == 100) {
                    progressBar.setVisibility(GONE);
                    progressBar.setProgress(0);
                    evaluateJavascript(
                            "window.dispatchEvent(new Event('onProgressChangeFinish'));",
                            value -> {}
                    );
                }
                super.onProgressChanged(view, progress);
            }

            // replace video default poster
            @Override
            public Bitmap getDefaultVideoPoster() {
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }

            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                setVisibility(View.GONE);

                MainActivity mainActivity = (MainActivity) getContext();
                if(fullscreen != null){
                    ((FrameLayout)mainActivity.getWindow().getDecorView()).
                            removeView(fullscreen);
                }

                fullscreen = view;
                fullscreen.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LOW_PROFILE
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);

                ((FrameLayout)mainActivity.getWindow().getDecorView()).
                        addView(fullscreen, new FrameLayout.LayoutParams(-1, -1));
                fullscreen.setVisibility(View.VISIBLE);
                mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                evaluateJavascript(
                        "window.dispatchEvent(new Event('onFullScreen'));",
                        value -> {}
                );
            }

            @Override
            public void onHideCustomView() {
                if (fullscreen == null){
                    return;
                }
                fullscreen.setVisibility(View.GONE);
                setVisibility(View.VISIBLE);
                MainActivity mainActivity = (MainActivity) getContext();
                mainActivity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                evaluateJavascript(
                        "window.dispatchEvent(new Event('exitFullScreen'));",
                        value -> {}
                );
            }
        });
    }

    @Override
    protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(VISIBLE);
    }


    public void injectJavaScript(InputStream jsInputStream) {
        String js = readInputStream(jsInputStream);
        if (js != null) {
            js_res.add(js);
        }
    }

    public void injectCSS(InputStream cssInputStream) {
        String css = readInputStream(cssInputStream);
        if (css != null) {
            String encodedCSS = Base64.getEncoder().encodeToString(css.getBytes());
            String js = String.format("(function() {" +
                    "var style = document.createElement('style');" +
                    "style.type = 'text/css';" +
                    "style.textContent = window.atob('%s');" +
                    "document.head.appendChild(style);" +
                    "})()", encodedCSS);
            js_res.add(js);
        }
    }

    private String readInputStream(InputStream inputStream) {
        try {
            byte[] buffer = new byte[1024];
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, bytesRead);
            }
            inputStream.close();
            return byteArrayOutputStream.toString();
        } catch (IOException e) {
            Log.e("InputStreamError", "Error reading input stream", e);
            return null;
        }
    }

}
