package com.senpai.tv;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Message;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

public class MainActivity extends Activity {
    // >>> Pour changer de site, modifier uniquement cette ligne <<<
    static final String HOME = "https://senpai-stream.surf";
    static final String HOST = Uri.parse(HOME).getHost();

    FrameLayout root;
    WebView web;
    Pointer pointer;
    WebChromeClient chrome;
    View customView;
    boolean cursorMode = true;
    long downTime, lastBack;

    /** Curseur affiche par-dessus la page, deplace avec la croix directionnelle. */
    static class Pointer extends View {
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = -1, y = -1;
        Pointer(Context c) { super(c); }
        @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
            if (x < 0) { x = w / 2f; y = h / 2f; }
        }
        @Override protected void onDraw(Canvas c) {
            p.setColor(Color.BLACK); c.drawCircle(x, y, 16, p);
            p.setColor(Color.WHITE); c.drawCircle(x, y, 12, p);
            p.setColor(Color.parseColor("#E91E63")); c.drawCircle(x, y, 5, p);
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        web = new WebView(this);
        web.setBackgroundColor(Color.BLACK);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));
        pointer = new Pointer(this);
        root.addView(pointer, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", "").replace(" Mobile", ""));
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            boolean ok(String url) {
                String h = Uri.parse(url).getHost();
                return h != null && (h.equals(HOST) || h.endsWith("." + HOST));
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                return r.isForMainFrame() && !ok(r.getUrl().toString());
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, String url) {
                return !ok(url);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r.isForMainFrame()) {
                    v.loadDataWithBaseURL(HOME, "<body style='background:#111;color:#fff;font:36px sans-serif;"
                        + "text-align:center;padding-top:18%'>Connexion impossible.<br><br>"
                        + "<a href='" + HOME + "' style='color:#E91E63'>R\u00e9essayer</a></body>",
                        "text/html", "UTF-8", null);
                }
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                recreate();
                return true;
            }
        });

        chrome = new WebChromeClient() {
            @Override public boolean onCreateWindow(WebView v, boolean d, boolean g, Message m) {
                return false; // bloque les pop-ups publicitaires
            }
            @Override public void onShowCustomView(View v, CustomViewCallback cb) {
                if (customView != null) { cb.onCustomViewHidden(); return; }
                customView = v;
                root.addView(v, root.indexOfChild(pointer), new FrameLayout.LayoutParams(-1, -1));
                web.setVisibility(View.INVISIBLE);
                v.requestFocus();
                setCursorMode(false, false);
            }
            @Override public void onHideCustomView() {
                if (customView == null) return;
                root.removeView(customView);
                customView = null;
                web.setVisibility(View.VISIBLE);
                web.requestFocus();
                setCursorMode(true, false);
            }
        };
        web.setWebChromeClient(chrome);
        web.loadUrl(HOME);
    }

    void setCursorMode(boolean on, boolean toast) {
        cursorMode = on;
        pointer.setVisibility(on ? View.VISIBLE : View.INVISIBLE);
        if (toast) Toast.makeText(this, on ? "Mode souris" : "Mode navigation (focus)", Toast.LENGTH_SHORT).show();
    }

    void goBack() {
        if (customView != null) {
            chrome.onHideCustomView();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            long now = SystemClock.uptimeMillis();
            if (now - lastBack < 2000) finish();
            else {
                lastBack = now;
                Toast.makeText(this, "Appuyez encore sur Retour pour quitter", Toast.LENGTH_SHORT).show();
            }
        }
    }

    void move(float dx, float dy) {
        int w = pointer.getWidth(), h = pointer.getHeight();
        if (customView == null) {
            // Defilement de la page quand le curseur touche un bord
            if (dy < 0 && pointer.y < 40) web.scrollBy(0, (int) (dy * 2));
            if (dy > 0 && pointer.y > h - 40) web.scrollBy(0, (int) (dy * 2));
            if (dx < 0 && pointer.x < 40) web.scrollBy((int) (dx * 2), 0);
            if (dx > 0 && pointer.x > w - 40) web.scrollBy((int) (dx * 2), 0);
        }
        pointer.x = Math.max(0, Math.min(w, pointer.x + dx));
        pointer.y = Math.max(0, Math.min(h, pointer.y + dy));
        pointer.invalidate();
    }

    void touch(int action) {
        View target = customView != null ? customView : web;
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) downTime = now;
        MotionEvent m = MotionEvent.obtain(downTime, now, action, pointer.x, pointer.y, 0);
        m.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        target.dispatchTouchEvent(m);
        m.recycle();
    }

    boolean handleCursor(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        float step = Math.min(14 + e.getRepeatCount() * 5, 70);
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_UP:    if (down) move(0, -step); return true;
            case KeyEvent.KEYCODE_DPAD_DOWN:  if (down) move(0, step);  return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:  if (down) move(-step, 0); return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT: if (down) move(step, 0);  return true;
            case KeyEvent.KEYCODE_DPAD_CENTER:
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
                if (down && e.getRepeatCount() == 0) touch(MotionEvent.ACTION_DOWN);
                else if (!down) touch(MotionEvent.ACTION_UP);
                return true;
        }
        return false;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean first = e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0;
        if (code == KeyEvent.KEYCODE_BACK) {
            if (first) goBack();
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) { // bouton "≡" de la telecommande
            if (first) setCursorMode(!cursorMode, true);
            return true;
        }
        if (cursorMode && handleCursor(e)) return true;
        return super.dispatchKeyEvent(e);
    }

    @Override protected void onResume() { super.onResume(); web.onResume(); }
    @Override protected void onPause() { web.onPause(); super.onPause(); }
    @Override protected void onDestroy() {
        root.removeAllViews();
        web.destroy();
        super.onDestroy();
    }
}
