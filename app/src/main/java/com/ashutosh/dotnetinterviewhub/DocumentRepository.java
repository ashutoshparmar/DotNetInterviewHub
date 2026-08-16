package com.ashutosh.dotnetinterviewhub;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class DocumentRepository extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "interview_hub.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLE = "documents";
    private final Context context;

    public DocumentRepository(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "display_order INTEGER NOT NULL DEFAULT 999," +
                "title TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "source_name TEXT," +
                "updated_at INTEGER NOT NULL," +
                "is_seeded INTEGER NOT NULL DEFAULT 0," +
                "bookmarked INTEGER NOT NULL DEFAULT 0)");
        seed(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Schema migrations will be added here for future releases.
    }

    private void seed(SQLiteDatabase db) {
        try (InputStream input = context.getAssets().open("seed_documents.json")) {
            JSONArray array = new JSONArray(readAll(input));
            db.beginTransaction();
            try {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject item = array.getJSONObject(i);
                    ContentValues values = new ContentValues();
                    values.put("display_order", item.optInt("order", i + 1));
                    values.put("title", item.getString("title"));
                    values.put("category", item.getString("category"));
                    values.put("content", item.getString("content"));
                    values.put("source_name", item.optString("sourceName", ""));
                    values.put("updated_at", System.currentTimeMillis());
                    values.put("is_seeded", 1);
                    values.put("bookmarked", 0);
                    db.insertOrThrow(TABLE, null, values);
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to seed the interview library", exception);
        }
    }

    public List<DocumentItem> search(String query, String category, boolean bookmarksOnly) {
        SQLiteDatabase db = getReadableDatabase();
        List<String> clauses = new ArrayList<>();
        List<String> args = new ArrayList<>();
        if (query != null && !query.trim().isEmpty()) {
            clauses.add("(title LIKE ? OR content LIKE ?)");
            String value = "%" + query.trim() + "%";
            args.add(value);
            args.add(value);
        }
        if (category != null && !category.equals("All topics")) {
            clauses.add("category = ?");
            args.add(category);
        }
        if (bookmarksOnly) clauses.add("bookmarked = 1");
        String where = clauses.isEmpty() ? null : TextUtils.join(" AND ", clauses);
        try (Cursor cursor = db.query(TABLE, null, where,
                args.isEmpty() ? null : args.toArray(new String[0]),
                null, null, "bookmarked DESC, display_order ASC, title COLLATE NOCASE ASC")) {
            List<DocumentItem> items = new ArrayList<>();
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
            return items;
        }
    }

    public DocumentItem get(long id) {
        try (Cursor cursor = getReadableDatabase().query(TABLE, null, "id = ?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public long insert(String title, String category, String content, String sourceName) {
        ContentValues values = values(title, category, content, sourceName);
        values.put("display_order", 999);
        values.put("is_seeded", 0);
        values.put("bookmarked", 0);
        return getWritableDatabase().insertOrThrow(TABLE, null, values);
    }

    public void update(long id, String title, String category, String content, String sourceName) {
        getWritableDatabase().update(TABLE, values(title, category, content, sourceName),
                "id = ?", new String[]{String.valueOf(id)});
    }

    public void setBookmarked(long id, boolean bookmarked) {
        ContentValues values = new ContentValues();
        values.put("bookmarked", bookmarked ? 1 : 0);
        getWritableDatabase().update(TABLE, values, "id = ?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) {
        getWritableDatabase().delete(TABLE, "id = ?", new String[]{String.valueOf(id)});
    }

    public int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }

    public List<String> categories() {
        Set<String> categories = new LinkedHashSet<>();
        categories.add("All topics");
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT category FROM " + TABLE + " ORDER BY category COLLATE NOCASE", null)) {
            while (cursor.moveToNext()) categories.add(cursor.getString(0));
        }
        return new ArrayList<>(categories);
    }

    private ContentValues values(String title, String category, String content, String sourceName) {
        ContentValues values = new ContentValues();
        values.put("title", title.trim());
        values.put("category", category.trim().isEmpty() ? "Imported" : category.trim());
        values.put("content", content.trim());
        values.put("source_name", sourceName == null ? "" : sourceName);
        values.put("updated_at", System.currentTimeMillis());
        return values;
    }

    private DocumentItem fromCursor(Cursor cursor) {
        return new DocumentItem(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")),
                cursor.getString(cursor.getColumnIndexOrThrow("source_name")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                cursor.getInt(cursor.getColumnIndexOrThrow("is_seeded")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("bookmarked")) == 1
        );
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
