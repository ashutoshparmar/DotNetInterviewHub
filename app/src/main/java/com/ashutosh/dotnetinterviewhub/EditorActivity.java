package com.ashutosh.dotnetinterviewhub;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class EditorActivity extends Activity {
    private DocumentRepository repository;
    private long documentId;
    private DocumentItem item;
    private EditText title;
    private EditText category;
    private EditText content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        documentId = getIntent().getLongExtra("document_id", -1);
        repository = new DocumentRepository(this);
        item = repository.get(documentId);
        if (item == null) { finish(); return; }
        setContentView(buildUi());
    }

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Ui.SURFACE);
        root.addView(Ui.header(this, "Edit interview topic", "Changes are stored offline on this device"));

        ScrollView scroll = new ScrollView(this);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 24));

        title = input("Title", item.title, false);
        form.addView(field("Document title", title));
        category = input("Category", item.category, false);
        form.addView(field("Category", category));
        content = input("Interview content", item.content, true);
        LinearLayout contentField = field("Content", content);
        contentField.addView(help("Formatting: start a heading with #, ## or ###. Imported DOCX headings are converted automatically."));
        form.addView(contentField);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = Ui.button(this, "Cancel", false);
        cancel.setOnClickListener(v -> finish());
        actions.addView(cancel, Ui.weightedButtonParams(this));
        Button save = Ui.button(this, "Save changes", true);
        save.setOnClickListener(v -> save());
        actions.addView(save, Ui.weightedButtonParams(this));
        form.addView(actions);
        scroll.addView(form);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        return root;
    }

    private EditText input(String hint, String value, boolean multiline) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setText(value);
        edit.setTextColor(Ui.INK);
        edit.setHintTextColor(Ui.MUTED);
        edit.setTextSize(multiline ? 15 : 16);
        edit.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 10));
        edit.setBackground(Ui.cardBackground(this));
        if (multiline) {
            edit.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE |
                    InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            edit.setGravity(android.view.Gravity.TOP | android.view.Gravity.START);
            edit.setMinLines(18);
        } else edit.setSingleLine(true);
        return edit;
    }

    private LinearLayout field(String labelText, View input) {
        LinearLayout field = new LinearLayout(this);
        field.setOrientation(LinearLayout.VERTICAL);
        field.setPadding(0, 0, 0, Ui.dp(this, 14));
        TextView label = new TextView(this);
        label.setText(labelText);
        label.setTextColor(Ui.DARK_BLUE);
        label.setTextSize(13);
        label.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD);
        label.setPadding(0, 0, 0, Ui.dp(this, 5));
        field.addView(label);
        field.addView(input, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return field;
    }

    private TextView help(String text) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(12);
        view.setTextColor(Ui.MUTED);
        view.setPadding(Ui.dp(this, 3), Ui.dp(this, 6), 0, 0);
        return view;
    }

    private void save() {
        String newTitle = title.getText().toString().trim();
        String newCategory = category.getText().toString().trim();
        String newContent = content.getText().toString().trim();
        if (newTitle.isEmpty() || newContent.isEmpty()) {
            Toast.makeText(this, "Title and content are required.", Toast.LENGTH_LONG).show();
            return;
        }
        repository.update(documentId, newTitle,
                newCategory.isEmpty() ? "Imported" : newCategory,
                newContent, item.sourceName);
        Toast.makeText(this, "Changes saved", Toast.LENGTH_SHORT).show();
        finish();
    }
}
