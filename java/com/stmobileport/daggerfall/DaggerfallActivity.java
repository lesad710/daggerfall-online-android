package com.stmobileport.daggerfall;

import java.io.File;
import java.io.FileInputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * Daggerfall Online (daggerfalljs.dev) in a WebView: the live client, so online play always speaks the servers' own
 * version (the open-source edition of 2026-11-02 already lags the live game). Daggerfall's data cannot be served by
 * the site: when the client has no stored copy its data seam (dataSource.js ensureArena2 / getBytes) fetches
 * ./arena2/NAME, and every such request - the page's and its service worker's - is answered here from the player's
 * ARENA2 folder (extra "arena2", names case-insensitive). Runs in its own process (:game).
 *
 * Offline mode (extra "web": a folder holding our build of the open-source edition): the same page served from that
 * folder at a local https origin, no network at all; its OFFLINE mode plays, its saves stay on the phone.
 */
public class DaggerfallActivity extends Activity {
    static final String URL = "https://daggerfalljs.dev/play/";
    static final String LOCAL_HOST = "appassets.androidplatform.net";
    private WebView web;
    private File webRoot;  // the offline build, or null for the live site
    private int cssWidth;  // the page's layout width in CSS pixels (the launcher's interface size; 0 = the site's own)
    private float uiScale;  // the page scale that fits cssWidth to the screen
    private final Map<String, File> arenaNames = new HashMap<>();

    @SuppressLint("SetJavaScriptEnabled")
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getAttributes().layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        // Interface size: the site lays out at the screen's width in CSS pixels (820 on a 2460-pixel, 3x phone) and its HUD
        // fills half the screen; a wider layout viewport, scaled down to fit, draws the same interface smaller.
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getRealMetrics(dm);
        float ui = getIntent().getFloatExtra("ui", 1f);
        if (ui < 0.99f) { cssWidth = Math.round(Math.max(dm.widthPixels, dm.heightPixels) / dm.density / ui); uiScale = ui; }
        String webDir = getIntent().getStringExtra("web");
        if (webDir != null) webRoot = new File(webDir);
        File[] list = new File(getIntent().getStringExtra("arena2")).listFiles();
        if (list != null) for (File f : list) arenaNames.put(f.getName().toUpperCase(Locale.ROOT), f);

        WebView.setWebContentsDebuggingEnabled(true);  // chrome://inspect from a PC over USB: the console of a player's report
        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setUseWideViewPort(true);        // honour the page's viewport width (the interface size above)
        s.setLoadWithOverviewMode(true);   // and fit that width to the screen
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) { return intercept(r); }
            // the interface size, set on the live document: the page itself comes from the site's service worker cache,
            // never through shouldInterceptRequest, so its viewport tag is changed in place (Chrome re-lays out on it)
            @Override public void onPageCommitVisible(WebView v, String url) { applyViewport(); }
            @Override public void onPageFinished(WebView v, String url) { applyViewport(); }
        });
        // the site's service worker fetches too: its requests never reach the WebViewClient above
        ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebResourceRequest r) { return intercept(r); }
        });
        setContentView(web);
        // the game's own render-scale door (systems/renderScale.js, ?renderscale=): the world drawn at this share of the
        // window and shown full size; 1 = the site's default
        String scale = getIntent().getStringExtra("renderscale");
        String url = webRoot != null ? "https://" + LOCAL_HOST + "/play/" : URL;
        web.loadUrl(scale == null || scale.equals("1") ? url : url + "?renderscale=" + scale);
    }

    /** The page's viewport widened to cssWidth and pinned at the scale that fits it (with only a width Chrome keeps the
     *  site's initial-scale=1 and crops instead of fitting). */
    private void applyViewport() {
        if (cssWidth <= 0) return;
        String sc = String.format(Locale.ROOT, "%.3f", uiScale);
        web.evaluateJavascript("(function(){var m=document.querySelector('meta[name=viewport]');if(!m){m=document.createElement('meta');"
            + "m.name='viewport';document.head.appendChild(m);}m.content='width=" + cssWidth + ", initial-scale=" + sc
            + ", minimum-scale=" + sc + ", maximum-scale=" + sc + ", viewport-fit=cover, user-scalable=no';"
            + "return innerWidth+'x'+innerHeight;})()", w -> android.util.Log.i("Daggerfall", "viewport " + cssWidth + " -> " + w));
    }

    private static final Map<String, String> MIME = new HashMap<>();
    static {
        String[][] t = {{"html", "text/html"}, {"js", "text/javascript"}, {"mjs", "text/javascript"}, {"css", "text/css"},
            {"json", "application/json"}, {"webmanifest", "application/manifest+json"}, {"png", "image/png"},
            {"jpg", "image/jpeg"}, {"gif", "image/gif"}, {"svg", "image/svg+xml"}, {"webp", "image/webp"}, {"ico", "image/x-icon"},
            {"mp3", "audio/mpeg"}, {"ogg", "audio/ogg"}, {"wav", "audio/wav"}, {"woff", "font/woff"}, {"woff2", "font/woff2"},
            {"ttf", "font/ttf"}, {"wasm", "application/wasm"}, {"txt", "text/plain"}};
        for (String[] e : t) MIME.put(e[0], e[1]);
    }

    /** ARENA2 files for ".../arena2/NAME"; in offline mode every other path of the local origin from the build folder. */
    private WebResourceResponse intercept(WebResourceRequest r) {
        String path = r.getUrl().getPath();
        WebResourceResponse a = arena2(path);
        if (a != null || webRoot == null || !LOCAL_HOST.equals(r.getUrl().getHost())) return a;
        File f = new File(webRoot, path == null ? "/" : path);
        if (f.isDirectory()) f = new File(f, "index.html");
        Map<String, String> headers = new HashMap<>();
        try {
            if (!f.getCanonicalPath().startsWith(webRoot.getCanonicalPath()) || !f.isFile())
                return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers, null);
            String n = f.getName(), ext = n.substring(n.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
            String mime = MIME.containsKey(ext) ? MIME.get(ext) : "application/octet-stream";
            headers.put("Content-Length", String.valueOf(f.length()));
            return new WebResourceResponse(mime, mime.startsWith("text/") ? "utf-8" : null, 200, "OK", headers, new FileInputStream(f));
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 500, "Error", headers, null);
        }
    }

    /** A file of the ARENA2 folder for ".../arena2/NAME"; null (the network) for anything else. */
    private WebResourceResponse arena2(String path) {
        if (path == null) return null;
        int a = path.toLowerCase(Locale.ROOT).lastIndexOf("/arena2/");
        if (a < 0) return null;
        File f = arenaNames.get(path.substring(a + 8).toUpperCase(Locale.ROOT));
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Origin", "*");
        // a cross-origin-isolated page (COOP/COEP, which WASM threads need) refuses any response without this
        headers.put("Cross-Origin-Resource-Policy", "cross-origin");
        if (f == null) return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", headers, null);
        try {
            headers.put("Content-Length", String.valueOf(f.length()));
            return new WebResourceResponse("application/octet-stream", null, 200, "OK", headers, new FileInputStream(f));
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 500, "Error", headers, null);
        }
    }

    /** The phone's Back is the game's Escape (its menus close with it). */
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (e.getRepeatCount() == 0) web.dispatchKeyEvent(new KeyEvent(e.getAction(), KeyEvent.KEYCODE_ESCAPE));
            return true;
        }
        return super.dispatchKeyEvent(e);
    }

    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override protected void onPause() { super.onPause(); web.onPause(); }
    @Override protected void onResume() { super.onResume(); web.onResume(); }

    @Override protected void onDestroy() {
        web.destroy();
        super.onDestroy();
        if (isFinishing()) android.os.Process.killProcess(android.os.Process.myPid());
    }
}
