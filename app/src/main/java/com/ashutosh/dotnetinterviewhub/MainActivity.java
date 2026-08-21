package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 1001;
    private DocumentRepository repository;
    private DocumentAdapter adapter;
    private SearchView searchView;
    private Spinner workspaceSpinner;
    private Spinner categorySpinner;
    private Spinner tagSpinner;
    private Spinner sortSpinner;
    private CheckBox bookmarksOnly;
    private TextView summary;
    private boolean refreshingFilters;
    private final ExecutorService importExecutor = Executors.newSingleThreadExecutor();
    private ProgressDialog importProgress;
    private volatile boolean destroyed;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new DocumentRepository(this);
        setContentView(buildUi());
        refreshWorkspaces();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, "Knowledge Hub", "Interview preparation • ChangeGuard • Offline"));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.VERTICAL);
        filters.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));

        searchView = new SearchView(this);
        searchView.setIconifiedByDefault(false); searchView.setQueryHint("Search titles, tags and complete content");
        searchView.setBackgroundColor(Color.WHITE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { refresh(); return true; }
            @Override public boolean onQueryTextChange(String text) { refresh(); return true; }
        });
        filters.addView(searchView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        workspaceSpinner = spinner();
        workspaceSpinner.setOnItemSelectedListener(listener(() -> refreshDependentFilters()));
        filters.addView(workspaceSpinner, matchSpinner());

        LinearLayout filterRow = new LinearLayout(this); filterRow.setOrientation(LinearLayout.HORIZONTAL);
        categorySpinner = spinner(); categorySpinner.setOnItemSelectedListener(listener(this::refresh));
        filterRow.addView(categorySpinner, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        tagSpinner = spinner(); tagSpinner.setOnItemSelectedListener(listener(this::refresh));
        filterRow.addView(tagSpinner, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        filters.addView(filterRow);

        LinearLayout sortRow = new LinearLayout(this); sortRow.setOrientation(LinearLayout.HORIZONTAL);
        sortSpinner = spinner();
        sortSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item,
                Arrays.asList("Library order", "Recently opened", "Recently updated", "Title")));
        sortSpinner.setOnItemSelectedListener(listener(this::refresh));
        sortRow.addView(sortSpinner, new LinearLayout.LayoutParams(0, Ui.dp(this, 46), 1));
        bookmarksOnly = new CheckBox(this); bookmarksOnly.setText("Bookmarks"); bookmarksOnly.setTextColor(Ui.INK);
        bookmarksOnly.setOnCheckedChangeListener((button, checked) -> refresh());
        sortRow.addView(bookmarksOnly, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 46)));
        filters.addView(sortRow);

        summary = new TextView(this); summary.setTextSize(12); summary.setTextColor(Ui.MUTED);
        summary.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 3)); filters.addView(summary);
        root.addView(filters);

        ListView list = new ListView(this); list.setDivider(null); list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false); list.setPadding(0, 0, 0, Ui.dp(this, 4));
        adapter = new DocumentAdapter(this); list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, DetailActivity.class); intent.putExtra("document_id", id); startActivity(intent);
        });
        root.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this); actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(this, 6), Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 7));
        Button importButton = Ui.button(this, "＋ Import", true); importButton.setOnClickListener(v -> chooseDocument());
        actions.addView(importButton, Ui.weightedButtonParams(this));
        Button addButton = Ui.button(this, "✎ New note", false); addButton.setOnClickListener(v -> createNote());
        actions.addView(addButton, Ui.weightedButtonParams(this));
        Button libraryButton = Ui.button(this, "⚙ Manage", false);
        libraryButton.setOnClickListener(v -> startActivity(new Intent(this, LibraryActivity.class)));
        actions.addView(libraryButton, Ui.weightedButtonParams(this));
        root.addView(actions);
        return root;
    }

    private Spinner spinner() { Spinner value = new Spinner(this); value.setBackgroundColor(Color.WHITE); return value; }
    private LinearLayout.LayoutParams matchSpinner() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 46));
        params.setMargins(0, Ui.dp(this, 5), 0, 0); return params;
    }
    private android.widget.AdapterView.OnItemSelectedListener listener(Runnable action) {
        return new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int pos, long id) {
                if (!refreshingFilters) action.run();
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        };
    }

    private WorkspaceItem selectedWorkspace() {
        Object selected = workspaceSpinner == null ? null : workspaceSpinner.getSelectedItem();
        return selected instanceof WorkspaceItem ? (WorkspaceItem) selected : new WorkspaceItem(0, "All workspaces", "");
    }

    private void createNote() {
        long workspaceId = selectedWorkspace().id;
        if (workspaceId == 0) workspaceId = DocumentRepository.INTERVIEW_WORKSPACE_ID;
        long id = repository.insert("New knowledge note", "Notes", "# New knowledge note\n\nAdd your content here.",
                "Manual note", workspaceId, "", "");
        openEditor(id);
    }

    private void chooseDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT); intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*"); intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "text/plain", "text/markdown"});
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData(); if (uri == null) return;
        importDocument(uri);
    }

    private void importDocument(Uri uri) {
        long selectedId = selectedWorkspace().id;
        long workspaceId = selectedId == 0 ? DocumentRepository.INTERVIEW_WORKSPACE_ID : selectedId;
        showImportProgress("Importing document", "Reading, checking and formatting the selected file…");
        importExecutor.execute(() -> {
            try {
                DocumentImport imported = DocumentImport.read(getContentResolver(), uri);
                long id = repository.insert(imported.suggestedTitle, "Imported", imported.content,
                        imported.fileName, workspaceId, "", "", imported.renderedHtml, imported.sourceFormat);
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    dismissImportProgress();
                    Toast.makeText(this, "Document imported safely. Add its workspace, folder and tags.",
                            Toast.LENGTH_LONG).show();
                    openEditor(id);
                });
            } catch (Exception exception) {
                String message = exception.getMessage() == null ? "The document could not be imported." : exception.getMessage();
                runOnUiThread(() -> {
                    if (destroyed || isFinishing()) return;
                    dismissImportProgress();
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void showImportProgress(String title, String message) {
        dismissImportProgress();
        importProgress = new ProgressDialog(this);
        importProgress.setTitle(title);
        importProgress.setMessage(message);
        importProgress.setIndeterminate(true);
        importProgress.setCancelable(false);
        importProgress.show();
    }

    private void dismissImportProgress() {
        if (importProgress != null) {
            try { importProgress.dismiss(); } catch (Exception ignored) {}
            importProgress = null;
        }
    }

    private void openEditor(long id) {
        Intent intent = new Intent(this, EditorActivity.class); intent.putExtra("document_id", id); startActivity(intent);
    }

    private void refreshWorkspaces() {
        long previous = selectedWorkspace().id;
        refreshingFilters = true;
        List<WorkspaceItem> workspaces = repository.workspaces(true);
        workspaceSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, workspaces));
        int position = 0;
        for (int i = 0; i < workspaces.size(); i++) if (workspaces.get(i).id == previous) position = i;
        workspaceSpinner.setSelection(position); refreshingFilters = false;
        refreshDependentFilters();
    }

    private void refreshDependentFilters() {
        if (workspaceSpinner == null) return;
        String oldCategory = categorySpinner.getSelectedItem() == null ? "All categories" : categorySpinner.getSelectedItem().toString();
        String oldTag = tagSpinner.getSelectedItem() == null ? "All tags" : tagSpinner.getSelectedItem().toString();
        long workspaceId = selectedWorkspace().id; refreshingFilters = true;
        List<String> categories = repository.categories(workspaceId);
        categorySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, categories));
        categorySpinner.setSelection(Math.max(0, categories.indexOf(oldCategory)));
        List<String> tags = repository.tags(workspaceId);
        tagSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, tags));
        tagSpinner.setSelection(Math.max(0, tags.indexOf(oldTag))); refreshingFilters = false; refresh();
    }

    private void refresh() {
        if (adapter == null || workspaceSpinner == null || categorySpinner.getSelectedItem() == null || tagSpinner.getSelectedItem() == null) return;
        String query = searchView == null ? "" : searchView.getQuery().toString();
        List<DocumentItem> items = repository.search(query, selectedWorkspace().id,
                categorySpinner.getSelectedItem().toString(), tagSpinner.getSelectedItem().toString(),
                bookmarksOnly != null && bookmarksOnly.isChecked(),
                sortSpinner == null || sortSpinner.getSelectedItem() == null ? "Library order" : sortSpinner.getSelectedItem().toString());
        adapter.submit(items);
        summary.setText(items.size() + " shown • " + repository.count() + " total • Stored offline");
    }

    @Override protected void onResume() {
        super.onResume(); if (repository != null && workspaceSpinner != null) refreshWorkspaces();
    }

    @Override protected void onDestroy() {
        destroyed = true;
        dismissImportProgress();
        importExecutor.shutdownNow();
        if (repository != null) repository.close();
        super.onDestroy();
    }
}
