package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class LibraryActivity extends Activity {
    private static final int REQUEST_EXPORT = 3001;
    private static final int REQUEST_RESTORE = 3002;
    private DocumentRepository repository;
    private LinearLayout workspaceList;
    private EditText workspaceName;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); repository = new DocumentRepository(this); setContentView(buildUi()); refreshWorkspaces();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, "Manage Knowledge Hub", "Workspaces • Backup • Restore"));
        ScrollView scroll = new ScrollView(this); LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL); content.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 24));

        content.addView(sectionTitle("Your workspaces"));
        workspaceList = new LinearLayout(this); workspaceList.setOrientation(LinearLayout.VERTICAL); content.addView(workspaceList);
        workspaceName = new EditText(this); workspaceName.setHint("New workspace name"); workspaceName.setSingleLine(true);
        workspaceName.setBackground(Ui.cardBackground(this)); workspaceName.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        content.addView(workspaceName, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        Button add = Ui.button(this, "＋ Add workspace", true); add.setOnClickListener(v -> addWorkspace()); content.addView(add, buttonParams());

        TextView backupTitle = sectionTitle("Protect your documents"); backupTitle.setPadding(0, Ui.dp(this, 22), 0, Ui.dp(this, 8)); content.addView(backupTitle);
        TextView help = new TextView(this);
        help.setText("Export one JSON backup containing all documents, workspaces, bookmarks, reading progress and version history. Keep it in Google Drive or another safe location.");
        help.setTextColor(Ui.MUTED); help.setTextSize(13); help.setPadding(0, 0, 0, Ui.dp(this, 8)); content.addView(help);
        LinearLayout backupActions = new LinearLayout(this); backupActions.setOrientation(LinearLayout.HORIZONTAL);
        Button export = Ui.button(this, "Export backup", true); export.setOnClickListener(v -> chooseExportLocation());
        backupActions.addView(export, Ui.weightedButtonParams(this));
        Button restore = Ui.button(this, "Restore backup", false); restore.setOnClickListener(v -> chooseBackup());
        backupActions.addView(restore, Ui.weightedButtonParams(this)); content.addView(backupActions);

        Button back = Ui.button(this, "← Back to documents", false); back.setOnClickListener(v -> finish()); content.addView(back, buttonParams());
        scroll.addView(content); root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1)); return root;
    }

    private TextView sectionTitle(String text) {
        TextView title = new TextView(this); title.setText(text); title.setTextColor(Ui.DARK_BLUE); title.setTextSize(18);
        title.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); title.setPadding(0, 0, 0, Ui.dp(this, 8)); return title;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        params.setMargins(0, Ui.dp(this, 8), 0, 0); return params;
    }

    private void refreshWorkspaces() {
        workspaceList.removeAllViews();
        for (WorkspaceItem item : repository.workspaces(false)) {
            TextView row = new TextView(this); row.setText("• " + item.name + "\n  " + item.description);
            row.setTextColor(Ui.INK); row.setTextSize(14); row.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
            workspaceList.addView(row);
        }
    }

    private void addWorkspace() {
        try {
            repository.addWorkspace(workspaceName.getText().toString()); workspaceName.setText(""); refreshWorkspaces();
            Toast.makeText(this, "Workspace added", Toast.LENGTH_SHORT).show();
        } catch (Exception exception) { Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void chooseExportLocation() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json"); String date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
        intent.putExtra(Intent.EXTRA_TITLE, "knowledge-hub-backup-" + date + ".json"); startActivityForResult(intent, REQUEST_EXPORT);
    }

    private void chooseBackup() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json"); startActivityForResult(intent, REQUEST_RESTORE);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT) exportBackup(uri);
        else if (requestCode == REQUEST_RESTORE) confirmRestore(uri);
    }

    private void exportBackup(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) throw new IllegalStateException("The selected location could not be opened.");
            repository.writeBackup(output); Toast.makeText(this, "Backup exported successfully", Toast.LENGTH_LONG).show();
        } catch (Exception exception) { Toast.makeText(this, "Backup failed: " + exception.getMessage(), Toast.LENGTH_LONG).show(); }
    }

    private void confirmRestore(Uri uri) {
        new AlertDialog.Builder(this).setTitle("Restore this backup?")
                .setMessage("The backup will replace the current local library. Export the current library first if you may need it.")
                .setNegativeButton("Cancel", null).setPositiveButton("Restore", (dialog, which) -> restoreBackup(uri)).show();
    }

    private void restoreBackup(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IllegalStateException("The selected backup could not be opened.");
            repository.restoreBackup(input); refreshWorkspaces(); Toast.makeText(this, "Library restored successfully", Toast.LENGTH_LONG).show();
        } catch (Exception exception) { Toast.makeText(this, "Restore failed: " + exception.getMessage(), Toast.LENGTH_LONG).show(); }
    }
}
