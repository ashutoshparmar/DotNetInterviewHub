package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class VersionHistoryActivity extends Activity {
    private DocumentRepository repository;
    private long documentId;
    private List<DocumentVersion> versions;
    private ListView list;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); documentId = getIntent().getLongExtra("document_id", -1);
        repository = new DocumentRepository(this); setContentView(buildUi()); refresh();
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, "Document version history", "Restore an earlier copy without losing the current one"));
        TextView help = new TextView(this); help.setText("A version is created automatically before every edit or document replacement.");
        help.setTextColor(Ui.MUTED); help.setTextSize(13); help.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 8)); root.addView(help);
        list = new ListView(this); list.setOnItemClickListener((parent, view, position, id) -> confirmRestore(versions.get(position)));
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        Button back = Ui.button(this, "← Back to document", false); back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 54));
        params.setMargins(Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 10)); root.addView(back, params); return root;
    }

    private void refresh() {
        versions = repository.versions(documentId); List<String> labels = new ArrayList<>();
        for (DocumentVersion version : versions) {
            String date = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(new Date(version.savedAt));
            labels.add(date + "\n" + version.title + " • " + (version.sourceName == null ? "" : version.sourceName));
        }
        if (labels.isEmpty()) labels.add("No earlier versions yet. The first version will appear after you edit or replace this document.");
        list.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, labels));
        list.setEnabled(!versions.isEmpty());
    }

    private void confirmRestore(DocumentVersion version) {
        new AlertDialog.Builder(this).setTitle("Restore this version?")
                .setMessage("Your current document will also be retained in version history.")
                .setNegativeButton("Cancel", null).setPositiveButton("Restore", (dialog, which) -> {
                    repository.restoreVersion(version.id); Toast.makeText(this, "Earlier version restored", Toast.LENGTH_LONG).show(); finish();
                }).show();
    }
}
