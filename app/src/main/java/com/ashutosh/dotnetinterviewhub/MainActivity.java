package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
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

import java.util.List;

public class MainActivity extends Activity {
    private static final int REQUEST_IMPORT = 1001;
    private DocumentRepository repository;
    private DocumentAdapter adapter;
    private SearchView searchView;
    private Spinner categorySpinner;
    private CheckBox bookmarksOnly;
    private TextView summary;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        repository = new DocumentRepository(this);
        setContentView(buildUi());
        refreshCategories();
        refresh();
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, ".NET Interview Hub",
                "Ashutosh Parmar • Offline senior interview revision"));

        LinearLayout filters = new LinearLayout(this);
        filters.setOrientation(LinearLayout.VERTICAL);
        filters.setPadding(Ui.dp(this, 12), Ui.dp(this, 8), Ui.dp(this, 12), Ui.dp(this, 4));

        searchView = new SearchView(this);
        searchView.setIconifiedByDefault(false);
        searchView.setQueryHint("Search every document and answer");
        searchView.setBackgroundColor(Color.WHITE);
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override public boolean onQueryTextSubmit(String query) { refresh(); return true; }
            @Override public boolean onQueryTextChange(String text) { refresh(); return true; }
        });
        filters.addView(searchView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 52)));

        LinearLayout filterRow = new LinearLayout(this);
        filterRow.setOrientation(LinearLayout.HORIZONTAL);
        filterRow.setPadding(0, Ui.dp(this, 6), 0, 0);
        categorySpinner = new Spinner(this);
        categorySpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> p, View v, int position, long id) { refresh(); }
            @Override public void onNothingSelected(android.widget.AdapterView<?> p) {}
        });
        filterRow.addView(categorySpinner, new LinearLayout.LayoutParams(0, Ui.dp(this, 48), 1));
        bookmarksOnly = new CheckBox(this);
        bookmarksOnly.setText("Bookmarks");
        bookmarksOnly.setTextColor(Ui.INK);
        bookmarksOnly.setOnCheckedChangeListener((button, checked) -> refresh());
        filterRow.addView(bookmarksOnly, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, Ui.dp(this, 48)));
        filters.addView(filterRow);

        summary = new TextView(this);
        summary.setTextSize(12);
        summary.setTextColor(Ui.MUTED);
        summary.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 3));
        filters.addView(summary);
        root.addView(filters);

        ListView list = new ListView(this);
        list.setDivider(null);
        list.setSelector(android.R.color.transparent);
        list.setClipToPadding(false);
        list.setPadding(0, 0, 0, Ui.dp(this, 4));
        adapter = new DocumentAdapter(this);
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Intent intent = new Intent(this, DetailActivity.class);
            intent.putExtra("document_id", id);
            startActivity(intent);
        });
        root.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 8));
        Button importButton = Ui.button(this, "＋ Import DOCX / TXT", true);
        importButton.setOnClickListener(v -> chooseDocument());
        actions.addView(importButton, Ui.weightedButtonParams(this));
        Button addButton = Ui.button(this, "✎ Create note", false);
        addButton.setOnClickListener(v -> {
            long id = repository.insert("New interview note", "Imported", "# New interview note\n\nAdd your content here.", "Manual note");
            openEditor(id);
        });
        actions.addView(addButton, Ui.weightedButtonParams(this));
        root.addView(actions);
        return root;
    }

    private void chooseDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "text/plain", "text/markdown"
        });
        startActivityForResult(intent, REQUEST_IMPORT);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQUEST_IMPORT || resultCode != RESULT_OK || data == null) return;
        Uri uri = data.getData();
        if (uri == null) return;
        try {
            DocumentImport imported = DocumentImport.read(getContentResolver(), uri);
            long id = repository.insert(imported.suggestedTitle, "Imported", imported.content, imported.fileName);
            Toast.makeText(this, "Document imported. Review its title and category.", Toast.LENGTH_LONG).show();
            openEditor(id);
        } catch (Exception exception) {
            Toast.makeText(this, exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void openEditor(long id) {
        Intent intent = new Intent(this, EditorActivity.class);
        intent.putExtra("document_id", id);
        startActivity(intent);
    }

    private void refreshCategories() {
        String previous = categorySpinner.getSelectedItem() == null ? "All topics" : categorySpinner.getSelectedItem().toString();
        List<String> categories = repository.categories();
        ArrayAdapter<String> categoriesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, categories);
        categorySpinner.setAdapter(categoriesAdapter);
        int selected = Math.max(0, categories.indexOf(previous));
        categorySpinner.setSelection(selected);
    }

    private void refresh() {
        if (adapter == null || categorySpinner == null || categorySpinner.getSelectedItem() == null) return;
        String query = searchView == null ? "" : searchView.getQuery().toString();
        String category = categorySpinner.getSelectedItem().toString();
        List<DocumentItem> items = repository.search(query, category,
                bookmarksOnly != null && bookmarksOnly.isChecked());
        adapter.submit(items);
        summary.setText(items.size() + " shown • " + repository.count() + " total documents • Stored offline");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (repository != null && categorySpinner != null) {
            refreshCategories();
            refresh();
        }
    }
}
