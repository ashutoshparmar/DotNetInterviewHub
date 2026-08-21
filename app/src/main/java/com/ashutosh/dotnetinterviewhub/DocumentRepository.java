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
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Central data gateway for the local-first knowledge library. */
public class DocumentRepository extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "interview_hub.db";
    private static final int DATABASE_VERSION = 3;
    private static final String TABLE = "documents";
    public static final long INTERVIEW_WORKSPACE_ID = 1;
    public static final long CHANGEGUARD_WORKSPACE_ID = 2;
    private final Context context;

    public DocumentRepository(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        setWriteAheadLoggingEnabled(true);
    }

    @Override public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        createWorkspaceTable(db);
        insertDefaultWorkspaces(db);
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "display_order INTEGER NOT NULL DEFAULT 999," +
                "title TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "source_name TEXT," +
                "rendered_html TEXT NOT NULL DEFAULT ''," +
                "source_format TEXT NOT NULL DEFAULT 'text'," +
                "updated_at INTEGER NOT NULL," +
                "is_seeded INTEGER NOT NULL DEFAULT 0," +
                "bookmarked INTEGER NOT NULL DEFAULT 0," +
                "workspace_id INTEGER NOT NULL DEFAULT 1," +
                "folder_name TEXT NOT NULL DEFAULT ''," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "last_opened_at INTEGER NOT NULL DEFAULT 0," +
                "reading_progress INTEGER NOT NULL DEFAULT 0," +
                "FOREIGN KEY(workspace_id) REFERENCES workspaces(id))");
        createVersionTable(db);
        createSearchIndex(db);
        seed(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            createWorkspaceTable(db);
            insertDefaultWorkspaces(db);
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN workspace_id INTEGER NOT NULL DEFAULT 1");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN folder_name TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN tags TEXT NOT NULL DEFAULT ''");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN last_opened_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN reading_progress INTEGER NOT NULL DEFAULT 0");
            createVersionTable(db);
            createSearchIndex(db);
            db.execSQL("INSERT INTO document_search(document_id,title,content,tags) SELECT id,title,content,tags FROM documents");
        }
        if (oldVersion < 3) {
            addColumnIfMissing(db, TABLE, "rendered_html", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(db, TABLE, "source_format", "TEXT NOT NULL DEFAULT 'text'");
            createVersionTable(db);
            addColumnIfMissing(db, "document_versions", "rendered_html", "TEXT NOT NULL DEFAULT ''");
            addColumnIfMissing(db, "document_versions", "source_format", "TEXT NOT NULL DEFAULT 'text'");
        }
    }

    private void createWorkspaceTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS workspaces (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "name TEXT NOT NULL COLLATE NOCASE UNIQUE," +
                "description TEXT NOT NULL DEFAULT ''," +
                "display_order INTEGER NOT NULL DEFAULT 999," +
                "created_at INTEGER NOT NULL)");
    }

    private void insertDefaultWorkspaces(SQLiteDatabase db) {
        long now = System.currentTimeMillis();
        db.execSQL("INSERT OR IGNORE INTO workspaces(id,name,description,display_order,created_at) VALUES(1,?,?,1,?)",
                new Object[]{"Interview Preparation", "Senior .NET interview documents", now});
        db.execSQL("INSERT OR IGNORE INTO workspaces(id,name,description,display_order,created_at) VALUES(2,?,?,2,?)",
                new Object[]{"ChangeGuard", "Product and engineering knowledge", now});
    }

    private void createVersionTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS document_versions (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "document_id INTEGER NOT NULL," +
                "title TEXT NOT NULL," +
                "category TEXT NOT NULL," +
                "content TEXT NOT NULL," +
                "source_name TEXT," +
                "rendered_html TEXT NOT NULL DEFAULT ''," +
                "source_format TEXT NOT NULL DEFAULT 'text'," +
                "workspace_id INTEGER NOT NULL," +
                "folder_name TEXT NOT NULL DEFAULT ''," +
                "tags TEXT NOT NULL DEFAULT ''," +
                "saved_at INTEGER NOT NULL," +
                "FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE CASCADE)");
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_versions_document ON document_versions(document_id,saved_at DESC)");
    }

    private static void addColumnIfMissing(SQLiteDatabase db, String table, String column, String definition) {
        try (Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null)) {
            while (cursor.moveToNext()) {
                if (column.equals(cursor.getString(cursor.getColumnIndexOrThrow("name")))) return;
            }
        }
        db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
    }

    private void createSearchIndex(SQLiteDatabase db) {
        db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS document_search USING fts4(" +
                "document_id INTEGER, title TEXT, content TEXT, tags TEXT, tokenize=unicode61)");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS documents_search_insert AFTER INSERT ON documents BEGIN " +
                "INSERT INTO document_search(document_id,title,content,tags) VALUES(new.id,new.title,new.content,new.tags); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS documents_search_update AFTER UPDATE OF title,content,tags ON documents BEGIN " +
                "DELETE FROM document_search WHERE document_id=old.id; " +
                "INSERT INTO document_search(document_id,title,content,tags) VALUES(new.id,new.title,new.content,new.tags); END");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS documents_search_delete AFTER DELETE ON documents BEGIN " +
                "DELETE FROM document_search WHERE document_id=old.id; END");
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
                    values.put("workspace_id", INTERVIEW_WORKSPACE_ID);
                    values.put("folder_name", "Interview Guides");
                    values.put("tags", "interview,.net");
                    db.insertOrThrow(TABLE, null, values);
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to seed the interview library", exception);
        }
    }

    public List<DocumentItem> search(String query, long workspaceId, String category,
                                     String tag, boolean bookmarksOnly, String sort) {
        List<String> clauses = new ArrayList<>();
        List<String> args = new ArrayList<>();
        String trimmed = query == null ? "" : query.trim();
        if (!trimmed.isEmpty()) {
            String fts = ftsQuery(trimmed);
            if (!fts.isEmpty()) {
                clauses.add("d.id IN (SELECT document_id FROM document_search WHERE document_search MATCH ?)");
                args.add(fts);
            }
        }
        if (workspaceId > 0) { clauses.add("d.workspace_id=?"); args.add(String.valueOf(workspaceId)); }
        if (category != null && !category.equals("All categories")) { clauses.add("d.category=?"); args.add(category); }
        if (tag != null && !tag.equals("All tags")) {
            clauses.add("(',' || lower(d.tags) || ',') LIKE ?");
            args.add("%," + tag.toLowerCase(Locale.ROOT) + ",%");
        }
        if (bookmarksOnly) clauses.add("d.bookmarked=1");
        String where = clauses.isEmpty() ? "" : " WHERE " + TextUtils.join(" AND ", clauses);
        String order;
        if ("Recently opened".equals(sort)) order = "d.last_opened_at DESC,d.updated_at DESC";
        else if ("Recently updated".equals(sort)) order = "d.updated_at DESC";
        else if ("Title".equals(sort)) order = "d.title COLLATE NOCASE ASC";
        else order = "d.bookmarked DESC,d.display_order ASC,d.title COLLATE NOCASE ASC";
        String sql = "SELECT d.*,w.name AS workspace_name FROM documents d JOIN workspaces w ON w.id=d.workspace_id" +
                where + " ORDER BY " + order;
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args.toArray(new String[0]))) {
            List<DocumentItem> items = new ArrayList<>();
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
            return items;
        }
    }

    public List<DocumentItem> search(String query, String category, boolean bookmarksOnly) {
        String actual = category == null || category.equals("All topics") ? "All categories" : category;
        return search(query, 0, actual, "All tags", bookmarksOnly, "Library order");
    }

    public DocumentItem get(long id) {
        String sql = "SELECT d.*,w.name AS workspace_name FROM documents d JOIN workspaces w ON w.id=d.workspace_id WHERE d.id=?";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, new String[]{String.valueOf(id)})) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public long insert(String title, String category, String content, String sourceName) {
        return insert(title, category, content, sourceName, INTERVIEW_WORKSPACE_ID, "", "");
    }

    public long insert(String title, String category, String content, String sourceName,
                       long workspaceId, String folderName, String tags) {
        return insert(title, category, content, sourceName, workspaceId, folderName, tags, "", "text");
    }

    public long insert(String title, String category, String content, String sourceName,
                       long workspaceId, String folderName, String tags,
                       String renderedHtml, String sourceFormat) {
        ContentValues values = values(title, category, content, sourceName, workspaceId, folderName, tags,
                renderedHtml, sourceFormat);
        values.put("display_order", 999); values.put("is_seeded", 0); values.put("bookmarked", 0);
        return getWritableDatabase().insertOrThrow(TABLE, null, values);
    }

    public void update(long id, String title, String category, String content, String sourceName) {
        DocumentItem existing = get(id);
        if (existing != null) update(id, title, category, content, sourceName,
                existing.workspaceId, existing.folderName, existing.tags);
    }

    public void update(long id, String title, String category, String content, String sourceName,
                       long workspaceId, String folderName, String tags) {
        update(id, title, category, content, sourceName, workspaceId, folderName, tags, "", "text");
    }

    public void update(long id, String title, String category, String content, String sourceName,
                       long workspaceId, String folderName, String tags,
                       String renderedHtml, String sourceFormat) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            saveCurrentVersion(db, id);
            db.update(TABLE, values(title, category, content, sourceName, workspaceId, folderName, tags,
                            renderedHtml, sourceFormat),
                    "id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void saveCurrentVersion(SQLiteDatabase db, long documentId) {
        db.execSQL("INSERT INTO document_versions(document_id,title,category,content,source_name,rendered_html,source_format,workspace_id,folder_name,tags,saved_at) " +
                        "SELECT id,title,category,content,source_name,rendered_html,source_format,workspace_id,folder_name,tags,? FROM documents WHERE id=?",
                new Object[]{System.currentTimeMillis(), documentId});
    }

    public List<DocumentVersion> versions(long documentId) {
        try (Cursor cursor = getReadableDatabase().query("document_versions",
                new String[]{"id","document_id","title","source_name","saved_at"}, "document_id=?",
                new String[]{String.valueOf(documentId)}, null, null, "saved_at DESC")) {
            List<DocumentVersion> result = new ArrayList<>();
            while (cursor.moveToNext()) result.add(new DocumentVersion(cursor.getLong(0), cursor.getLong(1),
                    cursor.getString(2), cursor.getString(3), cursor.getLong(4)));
            return result;
        }
    }

    public void restoreVersion(long versionId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try (Cursor cursor = db.query("document_versions", null, "id=?",
                new String[]{String.valueOf(versionId)}, null, null, null)) {
            if (!cursor.moveToFirst()) return;
            long documentId = cursor.getLong(cursor.getColumnIndexOrThrow("document_id"));
            saveCurrentVersion(db, documentId);
            ContentValues values = new ContentValues();
            copy(cursor, values, "title"); copy(cursor, values, "category"); copy(cursor, values, "content");
            copy(cursor, values, "source_name"); copy(cursor, values, "rendered_html");
            copy(cursor, values, "source_format"); copy(cursor, values, "workspace_id");
            copy(cursor, values, "folder_name"); copy(cursor, values, "tags");
            values.put("updated_at", System.currentTimeMillis());
            db.update(TABLE, values, "id=?", new String[]{String.valueOf(documentId)});
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void copy(Cursor cursor, ContentValues values, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        if (cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER) values.put(column, cursor.getLong(index));
        else values.put(column, cursor.getString(index));
    }

    public void setBookmarked(long id, boolean bookmarked) {
        ContentValues values = new ContentValues(); values.put("bookmarked", bookmarked ? 1 : 0);
        getWritableDatabase().update(TABLE, values, "id=?", new String[]{String.valueOf(id)});
    }

    public void markOpened(long id) {
        ContentValues values = new ContentValues(); values.put("last_opened_at", System.currentTimeMillis());
        getWritableDatabase().update(TABLE, values, "id=?", new String[]{String.valueOf(id)});
    }

    public void saveReadingProgress(long id, int percent) {
        ContentValues values = new ContentValues(); values.put("reading_progress", Math.max(0, Math.min(100, percent)));
        getWritableDatabase().update(TABLE, values, "id=?", new String[]{String.valueOf(id)});
    }

    public void delete(long id) { getWritableDatabase().delete(TABLE, "id=?", new String[]{String.valueOf(id)}); }

    public int count() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM " + TABLE, null)) {
            cursor.moveToFirst(); return cursor.getInt(0);
        }
    }

    public List<WorkspaceItem> workspaces(boolean includeAll) {
        List<WorkspaceItem> result = new ArrayList<>();
        if (includeAll) result.add(new WorkspaceItem(0, "All workspaces", ""));
        try (Cursor cursor = getReadableDatabase().query("workspaces", new String[]{"id","name","description"},
                null, null, null, null, "display_order ASC,name COLLATE NOCASE ASC")) {
            while (cursor.moveToNext()) result.add(new WorkspaceItem(cursor.getLong(0), cursor.getString(1), cursor.getString(2)));
        }
        return result;
    }

    public long addWorkspace(String name) {
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty()) throw new IllegalArgumentException("Workspace name is required.");
        ContentValues values = new ContentValues(); values.put("name", clean);
        values.put("description", "Personal knowledge workspace"); values.put("display_order", 999);
        values.put("created_at", System.currentTimeMillis());
        long id = getWritableDatabase().insert("workspaces", null, values);
        if (id < 0) throw new IllegalArgumentException("That workspace already exists.");
        return id;
    }

    public List<String> categories(long workspaceId) {
        Set<String> result = new LinkedHashSet<>(); result.add("All categories");
        String where = workspaceId > 0 ? " WHERE workspace_id=?" : "";
        String[] args = workspaceId > 0 ? new String[]{String.valueOf(workspaceId)} : null;
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT category FROM documents" + where + " ORDER BY category COLLATE NOCASE", args)) {
            while (cursor.moveToNext()) result.add(cursor.getString(0));
        }
        return new ArrayList<>(result);
    }

    public List<String> categories() { List<String> result = categories(0); result.set(0, "All topics"); return result; }

    public List<String> tags(long workspaceId) {
        Set<String> result = new LinkedHashSet<>(); result.add("All tags");
        String where = workspaceId > 0 ? " WHERE workspace_id=?" : "";
        String[] args = workspaceId > 0 ? new String[]{String.valueOf(workspaceId)} : null;
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT tags FROM documents" + where, args)) {
            while (cursor.moveToNext()) for (String tag : cursor.getString(0).split(","))
                if (!tag.trim().isEmpty()) result.add(tag.trim());
        }
        return new ArrayList<>(result);
    }

    public void writeBackup(OutputStream output) throws Exception {
        JSONObject root = new JSONObject(); root.put("format", "dotnet-interview-hub-backup");
        root.put("version", 3); root.put("createdAt", System.currentTimeMillis());
        root.put("workspaces", tableAsJson("SELECT * FROM workspaces ORDER BY id"));
        root.put("documents", tableAsJson("SELECT * FROM documents ORDER BY id"));
        root.put("versions", tableAsJson("SELECT * FROM document_versions ORDER BY id"));
        output.write(root.toString(2).getBytes(StandardCharsets.UTF_8)); output.flush();
    }

    private JSONArray tableAsJson(String sql) throws Exception {
        JSONArray rows = new JSONArray();
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, null)) {
            while (cursor.moveToNext()) {
                JSONObject row = new JSONObject();
                for (String column : cursor.getColumnNames()) {
                    int index = cursor.getColumnIndexOrThrow(column);
                    if (cursor.isNull(index)) row.put(column, JSONObject.NULL);
                    else if (cursor.getType(index) == Cursor.FIELD_TYPE_INTEGER) row.put(column, cursor.getLong(index));
                    else row.put(column, cursor.getString(index));
                }
                rows.put(row);
            }
        }
        return rows;
    }

    public void restoreBackup(InputStream input) throws Exception {
        JSONObject root = new JSONObject(readAll(input));
        if (!"dotnet-interview-hub-backup".equals(root.optString("format")))
            throw new IllegalArgumentException("This is not a Knowledge Hub backup file.");
        JSONArray workspaces = root.getJSONArray("workspaces");
        JSONArray documents = root.getJSONArray("documents");
        JSONArray versions = root.optJSONArray("versions");
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            db.delete("document_versions", null, null); db.delete(TABLE, null, null); db.delete("workspaces", null, null);
            insertRows(db, "workspaces", workspaces); insertRows(db, TABLE, documents);
            if (versions != null) insertRows(db, "document_versions", versions);
            insertDefaultWorkspaces(db); db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private static void insertRows(SQLiteDatabase db, String table, JSONArray rows) throws Exception {
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i); ContentValues values = new ContentValues(); JSONArray names = row.names();
            if (names == null) continue;
            for (int j = 0; j < names.length(); j++) {
                String name = names.getString(j); Object value = row.get(name);
                if (value == JSONObject.NULL) values.putNull(name);
                else if (value instanceof Number) values.put(name, ((Number) value).longValue());
                else values.put(name, value.toString());
            }
            db.insertOrThrow(table, null, values);
        }
    }

    private ContentValues values(String title, String category, String content, String sourceName,
                                 long workspaceId, String folderName, String tags,
                                 String renderedHtml, String sourceFormat) {
        ContentValues values = new ContentValues(); values.put("title", title.trim());
        values.put("category", category.trim().isEmpty() ? "Imported" : category.trim());
        values.put("content", content.trim()); values.put("source_name", sourceName == null ? "" : sourceName);
        values.put("rendered_html", renderedHtml == null ? "" : renderedHtml.trim());
        values.put("source_format", sourceFormat == null || sourceFormat.trim().isEmpty() ? "text" : sourceFormat.trim());
        values.put("workspace_id", workspaceId <= 0 ? INTERVIEW_WORKSPACE_ID : workspaceId);
        values.put("folder_name", folderName == null ? "" : folderName.trim()); values.put("tags", normalizeTags(tags));
        values.put("updated_at", System.currentTimeMillis()); return values;
    }

    private DocumentItem fromCursor(Cursor cursor) {
        DocumentItem item = new DocumentItem(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")), cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getString(cursor.getColumnIndexOrThrow("content")), cursor.getString(cursor.getColumnIndexOrThrow("source_name")),
                cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")), cursor.getInt(cursor.getColumnIndexOrThrow("is_seeded")) == 1,
                cursor.getInt(cursor.getColumnIndexOrThrow("bookmarked")) == 1);
        item.workspaceId = cursor.getLong(cursor.getColumnIndexOrThrow("workspace_id"));
        item.workspaceName = cursor.getString(cursor.getColumnIndexOrThrow("workspace_name"));
        item.folderName = cursor.getString(cursor.getColumnIndexOrThrow("folder_name"));
        item.tags = cursor.getString(cursor.getColumnIndexOrThrow("tags"));
        item.lastOpenedAt = cursor.getLong(cursor.getColumnIndexOrThrow("last_opened_at"));
        item.readingProgress = cursor.getInt(cursor.getColumnIndexOrThrow("reading_progress"));
        item.renderedHtml = cursor.getString(cursor.getColumnIndexOrThrow("rendered_html"));
        item.sourceFormat = cursor.getString(cursor.getColumnIndexOrThrow("source_format"));
        return item;
    }

    private static String normalizeTags(String tags) {
        Set<String> clean = new LinkedHashSet<>();
        if (tags != null) for (String tag : tags.split(",")) {
            String value = tag.trim().toLowerCase(Locale.ROOT); if (!value.isEmpty()) clean.add(value);
        }
        return TextUtils.join(",", clean);
    }

    private static String ftsQuery(String query) {
        String clean = query.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}_]+", " ").trim();
        if (clean.isEmpty()) return "";
        StringBuilder result = new StringBuilder();
        for (String token : clean.split("\\s+")) {
            if (token.isEmpty()) continue; if (result.length() > 0) result.append(" AND "); result.append(token).append('*');
        }
        return result.toString();
    }

    private static String readAll(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(); byte[] buffer = new byte[8192]; int read;
        while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
        return output.toString(StandardCharsets.UTF_8.name());
    }
}
