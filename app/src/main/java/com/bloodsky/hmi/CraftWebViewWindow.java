package com.bloodsky.hmi;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.io.ByteArrayInputStream;

/** One explicitly approved foreground page. No background browser or automatic signal navigation. */
final class CraftWebViewWindow {
    private AlertDialog dialog;

    void open(Activity owner, String approvedUrl) {
        close();
        final String url = CraftWebViewPolicy.checkedUrl(approvedUrl);
        java.util.concurrent.atomic.AtomicBoolean active = new java.util.concurrent.atomic.AtomicBoolean(true);
        WebView view = new WebView(owner);
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setDomStorageEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setGeolocationEnabled(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 26) settings.setSafeBrowsingEnabled(true);
        CookieManager.getInstance().setAcceptCookie(false);
        CookieManager.getInstance().setAcceptThirdPartyCookies(view, false);
        view.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView browser, String target) {
                return !CraftWebViewPolicy.allows(url, target);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView browser, WebResourceRequest request) {
                return !"GET".equals(request.getMethod())
                        || !CraftWebViewPolicy.allows(url, request.getUrl().toString());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView browser, WebResourceRequest request) {
                try {
                    CraftWebViewFetch.Result result = CraftWebViewFetch.get(url,
                            request.getUrl().toString(), request.getMethod(), active::get,
                            target -> (java.net.HttpURLConnection) new java.net.URL(target).openConnection());
                    java.util.Map<String, String> headers = new java.util.HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("Content-Security-Policy", "default-src 'self'; script-src 'none'; "
                            + "connect-src 'none'; form-action 'none'; frame-src 'none'; object-src 'none'; base-uri 'none'");
                    return new WebResourceResponse(result.mime, result.encoding, 200, "OK", headers,
                            new ByteArrayInputStream(result.body));
                } catch (java.io.IOException | IllegalArgumentException blocked) { /* Fail closed. */ }
                return new WebResourceResponse("text/plain", "UTF-8", 403, "Blocked",
                        java.util.Collections.emptyMap(), new ByteArrayInputStream(new byte[0]));
            }
        });
        view.setDownloadListener((target, agent, disposition, mime, size) -> { /* Downloads disabled. */ });
        AlertDialog page = new AlertDialog.Builder(owner).setTitle(url).setView(view)
                .setPositiveButton("Закрыть", null).create();
        dialog = page;
        page.setOnDismissListener(ignored -> {
            active.set(false);
            view.stopLoading();
            view.onPause();
            view.destroy();
            if (dialog == page) dialog = null;
        });
        page.show();
        if (page.getWindow() != null) page.getWindow().setLayout(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT);
        view.loadUrl(url);
    }

    void close() {
        if (dialog != null) { AlertDialog old = dialog; dialog = null; old.dismiss(); }
    }
}
