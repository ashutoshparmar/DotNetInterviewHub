package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.ViewGroup;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;

/** Displays the format-preserving HTML generated locally from an imported DOCX. */
public class FormattedDocumentActivity extends Activity {
    private WebView webView;
    private DocumentRepository repository;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        long documentId = getIntent().getLongExtra("document_id", -1);
        repository = new DocumentRepository(this);
        DocumentItem item = repository.get(documentId);
        if (item == null || item.renderedHtml == null || item.renderedHtml.isEmpty()) {
            finish();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.WHITE);
        root.addView(Ui.header(this, item.title, "Formatted DOCX view • Pinch to zoom"));

        LinearLayout modes = new LinearLayout(this);
        modes.setOrientation(LinearLayout.HORIZONTAL);
        modes.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 2));
        Button formatted = Ui.button(this, "Formatted", true); formatted.setEnabled(false);
        modes.addView(formatted, Ui.weightedButtonParams(this));
        Button text = Ui.button(this, "Text", false); text.setOnClickListener(v -> finishWithMode("text"));
        modes.addView(text, Ui.weightedButtonParams(this));
        Button audio = Ui.button(this, "Audio", false); audio.setOnClickListener(v -> finishWithMode("audio"));
        modes.addView(audio, Ui.weightedButtonParams(this));
        root.addView(modes);

        webView = new WebView(this);
        webView.setBackgroundColor(Color.WHITE);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return openExternal(request.getUrl());
            }

            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternal(Uri.parse(url));
            }
        });
        webView.loadDataWithBaseURL(null, item.renderedHtml,
                "text/html", "UTF-8", null);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void finishWithMode(String mode) {
        Intent result = new Intent();
        result.putExtra("focus_mode", mode);
        setResult(RESULT_OK, result);
        finish();
    }

    private boolean openExternal(Uri uri) {
        String scheme = uri == null ? "" : uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)
                && !"mailto".equalsIgnoreCase(scheme)) return true;
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri)); }
        catch (Exception ignored) {}
        return true;
    }

    @Override protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.loadUrl("about:blank");
            webView.destroy();
        }
        if (repository != null) repository.close();
        super.onDestroy();
    }
}
