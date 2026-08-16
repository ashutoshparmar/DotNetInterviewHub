package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.Date;

public class DetailActivity extends Activity {
    private static final int REQUEST_REPLACE = 2001;
    private DocumentRepository repository;
    private long documentId;
    private DocumentItem item;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        documentId = getIntent().getLongExtra("document_id", -1);
        repository = new DocumentRepository(this);
        render();
    }

    private void render() {
        item = repository.get(documentId);
        if (item == null) { finish(); return; }

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, item.title, item.category));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(this, 6), Ui.dp(this, 5), Ui.dp(this, 6), Ui.dp(this, 3));
        Button back = Ui.button(this, "← Topics", false);
        back.setOnClickListener(v -> finish());
        actions.addView(back, Ui.weightedButtonParams(this));
        Button bookmark = Ui.button(this, item.bookmarked ? "★ Saved" : "☆ Bookmark", false);
        bookmark.setOnClickListener(v -> {
            repository.setBookmarked(documentId, !item.bookmarked);
            render();
        });
        actions.addView(bookmark, Ui.weightedButtonParams(this));
        Button edit = Ui.button(this, "✎ Edit", true);
        edit.setOnClickListener(v -> openEditor());
        actions.addView(edit, Ui.weightedButtonParams(this));
        root.addView(actions);

        ScrollView scroll = new ScrollView(this);
        LinearLayout contentColumn = new LinearLayout(this);
        contentColumn.setOrientation(LinearLayout.VERTICAL);
        contentColumn.setPadding(Ui.dp(this, 18), Ui.dp(this, 12), Ui.dp(this, 18), Ui.dp(this, 28));

        TextView metadata = new TextView(this);
        String source = item.sourceName == null || item.sourceName.isEmpty() ? "Manual note" : item.sourceName;
        metadata.setText("Source: " + source + "\nUpdated: " +
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(item.updatedAt)));
        metadata.setTextColor(Ui.MUTED);
        metadata.setTextSize(12);
        metadata.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        metadata.setBackground(Ui.cardBackground(this));
        contentColumn.addView(metadata, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView body = new TextView(this);
        body.setText(ContentFormatter.format(item.content));
        body.setTextColor(Ui.INK);
        body.setTextSize(16);
        body.setLineSpacing(Ui.dp(this, 4), 1.12f);
        body.setTextIsSelectable(true);
        body.setPadding(0, Ui.dp(this, 18), 0, Ui.dp(this, 12));
        contentColumn.addView(body);

        LinearLayout maintenance = new LinearLayout(this);
        maintenance.setOrientation(LinearLayout.VERTICAL);
        maintenance.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        maintenance.setBackgroundColor(Color.WHITE);
        TextView label = new TextView(this);
        label.setText("Maintain this topic");
        label.setTextSize(16);
        label.setTextColor(Ui.DARK_BLUE);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        maintenance.addView(label);
        TextView help = new TextView(this);
        help.setText("Replace the content from a modified DOCX/TXT file, or edit the title, category and text directly.");
        help.setTextSize(13);
        help.setTextColor(Ui.MUTED);
        help.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        maintenance.addView(help);

        LinearLayout maintenanceButtons = new LinearLayout(this);
        maintenanceButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button replace = Ui.button(this, "⇄ Replace document", true);
        replace.setOnClickListener(v -> chooseReplacement());
        maintenanceButtons.addView(replace, Ui.weightedButtonParams(this));
        Button delete = Ui.button(this, "Delete topic", false);
        delete.setOnClickListener(v -> confirmDelete());
        maintenanceButtons.addView(delete, Ui.weightedButtonParams(this));
        maintenance.addView(maintenanceButtons);
        contentColumn.addView(maintenance, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        scroll.addView(contentColumn);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        setContentView(root);
    }

    private void openEditor() {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("document_id", documentId);
        startActivity(intent);
    }

    private void chooseReplacement() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain", "text/markdown"
        });
        startActivityForResult(intent, REQUEST_REPLACE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_REPLACE || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            DocumentImport imported = DocumentImport.read(getContentResolver(), uri);
            repository.update(documentId, item.title, item.category, imported.content, imported.fileName);
            Toast.makeText(this, "The topic was replaced. Its title and category were preserved.", Toast.LENGTH_LONG).show();
            render();
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void confirmDelete() {
        new AlertDialog.Builder(this)
                .setTitle("Delete this topic?")
                .setMessage("This removes the topic and your edits from the app. The original DOCX file outside the app is not deleted.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    repository.delete(documentId);
                    Toast.makeText(this, "Topic deleted", Toast.LENGTH_SHORT).show();
                    finish();
                }).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null) render();
    }
}
