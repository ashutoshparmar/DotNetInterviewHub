package com.ashutosh.dotnetinterviewhub;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.InputStream;
import java.util.Locale;

public final class DocumentImport {
    public final String fileName;
    public final String suggestedTitle;
    public final String content;

    private DocumentImport(String fileName, String suggestedTitle, String content) {
        this.fileName = fileName;
        this.suggestedTitle = suggestedTitle;
        this.content = content;
    }

    public static DocumentImport read(ContentResolver resolver, Uri uri) throws Exception {
        String fileName = displayName(resolver, uri);
        String lower = fileName.toLowerCase(Locale.ROOT);
        String content;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("The selected document could not be opened.");
            if (lower.endsWith(".docx")) content = DocxTextExtractor.extract(input);
            else if (lower.endsWith(".txt") || lower.endsWith(".md")) content = DocxTextExtractor.readText(input);
            else throw new IllegalArgumentException("Please choose a DOCX, TXT or MD document.");
        }
        if (content.trim().isEmpty()) throw new IllegalArgumentException("No readable text was found in the selected document.");
        String title = fileName.replaceFirst("(?i)\\.(docx|txt|md)$", "")
                .replace('_', ' ').replaceAll("\\s+", " ").trim();
        return new DocumentImport(fileName, title, content);
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        String last = uri.getLastPathSegment();
        return last == null ? "Imported document.docx" : last;
    }
}
