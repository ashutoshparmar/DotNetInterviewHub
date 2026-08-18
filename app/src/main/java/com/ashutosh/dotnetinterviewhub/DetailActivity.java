package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Arrays;
import java.util.Date;

public class DetailActivity extends Activity implements SpeechController.Listener {
    private static final int REQUEST_REPLACE = 2001;
    private DocumentRepository repository;
    private long documentId;
    private DocumentItem item;
    private SpeechController speech;
    private ScrollView scroll;
    private TextView speechStatus;
    private Button readButton;
    private Button pauseButton;
    private Button stopButton;
    private boolean speechReady;
    private boolean speechSpeaking;
    private boolean speechPaused;
    private boolean hasResumedOnce;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); documentId = getIntent().getLongExtra("document_id", -1);
        repository = new DocumentRepository(this);
        float rate = getPreferences(MODE_PRIVATE).getFloat("speech_rate", 0.9f);
        speech = new SpeechController(this, rate, this); render(); repository.markOpened(documentId);
    }

    private void render() {
        item = repository.get(documentId); if (item == null) { finish(); return; }
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, item.title, item.workspaceName + " • " + item.category));

        LinearLayout actions = row();
        Button back = Ui.button(this, "← Library", false); back.setOnClickListener(v -> finish()); actions.addView(back, Ui.weightedButtonParams(this));
        Button bookmark = Ui.button(this, item.bookmarked ? "★ Saved" : "☆ Bookmark", false);
        bookmark.setOnClickListener(v -> { speech.stop(true); repository.setBookmarked(documentId, !item.bookmarked); render(); });
        actions.addView(bookmark, Ui.weightedButtonParams(this));
        Button edit = Ui.button(this, "✎ Edit", true); edit.setOnClickListener(v -> openEditor()); actions.addView(edit, Ui.weightedButtonParams(this));
        root.addView(actions);

        scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this, 18), Ui.dp(this, 10), Ui.dp(this, 18), Ui.dp(this, 28));

        TextView metadata = new TextView(this);
        String source = item.sourceName == null || item.sourceName.isEmpty() ? "Manual note" : item.sourceName;
        StringBuilder details = new StringBuilder("Workspace: ").append(item.workspaceName);
        if (item.folderName != null && !item.folderName.isEmpty()) details.append("\nFolder: ").append(item.folderName);
        details.append("\nCategory: ").append(item.category);
        if (item.tags != null && !item.tags.isEmpty()) details.append("\nTags: ").append(item.tags);
        details.append("\nSource: ").append(source).append("\nUpdated: ")
                .append(DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.updatedAt)));
        if (item.readingProgress > 0) details.append("\nReading progress: ").append(item.readingProgress).append('%');
        metadata.setText(details); metadata.setTextColor(Ui.MUTED); metadata.setTextSize(12);
        metadata.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10)); metadata.setBackground(Ui.cardBackground(this));
        content.addView(metadata, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildAudioPanel());
        TextView body = new TextView(this); body.setText(ContentFormatter.format(item.content)); body.setTextColor(Ui.INK);
        body.setTextSize(16); body.setLineSpacing(Ui.dp(this, 4), 1.12f); body.setTextIsSelectable(true);
        body.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 12)); content.addView(body);
        content.addView(buildMaintenancePanel());

        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root); updateAudioButtons();
        scroll.post(() -> {
            if (scroll.getChildCount() == 0) return;
            int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            scroll.scrollTo(0, range * item.readingProgress / 100);
        });
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(Ui.dp(this, 6), Ui.dp(this, 4), Ui.dp(this, 6), Ui.dp(this, 2)); return row;
    }

    private View buildAudioPanel() {
        LinearLayout panel = panel(); TextView title = panelTitle("Listen to this document"); panel.addView(title);
        speechStatus = new TextView(this); speechStatus.setText(speechReady ? "Ready to read this document aloud." : "Preparing text-to-speech…");
        speechStatus.setTextSize(13); speechStatus.setTextColor(Ui.MUTED); speechStatus.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 6)); panel.addView(speechStatus);

        LinearLayout speedRow = new LinearLayout(this); speedRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView speedLabel = new TextView(this); speedLabel.setText("Reading speed"); speedLabel.setTextColor(Ui.INK); speedLabel.setGravity(android.view.Gravity.CENTER_VERTICAL);
        speedRow.addView(speedLabel, new LinearLayout.LayoutParams(0, Ui.dp(this, 44), 1));
        Spinner speed = new Spinner(this); java.util.List<String> speeds = Arrays.asList("0.75x", "0.90x", "1.00x", "1.15x", "1.30x");
        speed.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, speeds));
        float savedRate = getPreferences(MODE_PRIVATE).getFloat("speech_rate", 0.9f);
        int selected = savedRate == .75f ? 0 : savedRate == 1f ? 2 : savedRate == 1.15f ? 3 : savedRate == 1.3f ? 4 : 1;
        speed.setSelection(selected);
        speed.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            boolean initial = true;
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) {
                float[] values = {.75f,.9f,1f,1.15f,1.3f};
                getPreferences(MODE_PRIVATE).edit().putFloat("speech_rate", values[position]).apply();
                speech.setRate(values[position]); if (initial) initial = false;
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        speedRow.addView(speed, new LinearLayout.LayoutParams(Ui.dp(this, 105), Ui.dp(this, 44))); panel.addView(speedRow);

        LinearLayout buttons = row();
        readButton = Ui.button(this, "▶ Read aloud", true); readButton.setOnClickListener(v -> speech.playOrResume(item.content));
        buttons.addView(readButton, Ui.weightedButtonParams(this));
        pauseButton = Ui.button(this, "Ⅱ Pause", false); pauseButton.setOnClickListener(v -> speech.pause());
        buttons.addView(pauseButton, Ui.weightedButtonParams(this));
        stopButton = Ui.button(this, "■ Stop", false); stopButton.setOnClickListener(v -> speech.stop(false));
        buttons.addView(stopButton, Ui.weightedButtonParams(this)); panel.addView(buttons); return panel;
    }

    private View buildMaintenancePanel() {
        LinearLayout panel = panel(); panel.setBackgroundColor(Color.WHITE); panel.addView(panelTitle("Maintain this document"));
        TextView help = new TextView(this);
        help.setText("Every edit or replacement keeps the previous version. You can restore it later from History.");
        help.setTextSize(13); help.setTextColor(Ui.MUTED); help.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8)); panel.addView(help);
        LinearLayout first = row();
        Button replace = Ui.button(this, "⇄ Replace", true); replace.setOnClickListener(v -> chooseReplacement()); first.addView(replace, Ui.weightedButtonParams(this));
        Button history = Ui.button(this, "↶ History", false); history.setOnClickListener(v -> openHistory()); first.addView(history, Ui.weightedButtonParams(this)); panel.addView(first);
        Button delete = Ui.button(this, "Delete document", false); delete.setOnClickListener(v -> confirmDelete());
        panel.addView(delete, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 50))); return panel;
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this); panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12)); panel.setBackground(Ui.cardBackground(this));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, Ui.dp(this, 12), 0, 0); panel.setLayoutParams(params); return panel;
    }

    private TextView panelTitle(String value) {
        TextView title = new TextView(this); title.setText(value); title.setTextSize(16); title.setTextColor(Ui.DARK_BLUE);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); return title;
    }

    private void openEditor() {
        speech.stop(true); Intent intent = new Intent(this, EditorActivity.class); intent.putExtra("document_id", documentId); startActivity(intent);
    }
    private void openHistory() {
        speech.stop(true); Intent intent = new Intent(this, VersionHistoryActivity.class); intent.putExtra("document_id", documentId); startActivity(intent);
    }
    private void chooseReplacement() {
        speech.stop(true); Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE); intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/vnd.openxmlformats-officedocument.wordprocessingml.document","text/plain","text/markdown"});
        startActivityForResult(intent, REQUEST_REPLACE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_REPLACE || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        try {
            DocumentImport imported = DocumentImport.read(getContentResolver(), data.getData());
            repository.update(documentId, item.title, item.category, imported.content, imported.fileName,
                    item.workspaceId, item.folderName, item.tags);
            Toast.makeText(this, "Document replaced. Previous version retained.", Toast.LENGTH_LONG).show(); render();
        } catch (Exception exception) { Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this).setTitle("Delete this document?")
                .setMessage("This removes the document and its local version history. Export a backup first if you may need it.")
                .setNegativeButton("Cancel", null).setPositiveButton("Delete", (dialog, which) -> {
                    speech.stop(true); repository.delete(documentId); Toast.makeText(this, "Document deleted", Toast.LENGTH_SHORT).show(); finish();
                }).show();
    }

    @Override public void onReady(boolean ready) { runOnUiThread(() -> { speechReady = ready; updateAudioButtons(); }); }
    @Override public void onState(String status, boolean speaking, boolean paused) {
        runOnUiThread(() -> { speechSpeaking = speaking; speechPaused = paused;
            if (speechStatus != null) speechStatus.setText(status); updateAudioButtons(); });
    }
    private void updateAudioButtons() {
        if (readButton != null) { readButton.setEnabled(speechReady); readButton.setText(speechPaused ? "▶ Resume" : speechSpeaking ? "↻ Restart" : "▶ Read aloud"); }
        if (pauseButton != null) pauseButton.setEnabled(speechReady && speechSpeaking);
        if (stopButton != null) stopButton.setEnabled(speechReady && (speechSpeaking || speechPaused));
    }

    @Override protected void onResume() {
        super.onResume(); if (repository != null && hasResumedOnce) render(); hasResumedOnce = true;
    }
    @Override protected void onPause() {
        if (scroll != null && scroll.getChildCount() > 0) {
            int range = Math.max(0, scroll.getChildAt(0).getHeight() - scroll.getHeight());
            int percent = range == 0 ? 100 : Math.round(scroll.getScrollY() * 100f / range);
            repository.saveReadingProgress(documentId, percent);
        }
        super.onPause();
    }
    @Override protected void onDestroy() { if (speech != null) speech.shutdown(); super.onDestroy(); }
}
