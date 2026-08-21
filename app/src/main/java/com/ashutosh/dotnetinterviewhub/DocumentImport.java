package com.ashutosh.dotnetinterviewhub;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Locale;

public final class DocumentImport {
    public final String fileName;
    public final String suggestedTitle;
    public final String content;
    public final String renderedHtml;
    public final String sourceFormat;

    private DocumentImport(String fileName, String suggestedTitle, String content,
                           String renderedHtml, String sourceFormat) {
        this.fileName = fileName;
        this.suggestedTitle = suggestedTitle;
        this.content = content;
        this.renderedHtml = renderedHtml;
        this.sourceFormat = sourceFormat;
    }

    public static DocumentImport read(ContentResolver resolver, Uri uri) throws Exception {
        String fileName = displayName(resolver, uri);
        long declaredSize = displaySize(resolver, uri);
        if (declaredSize > DocxSecurity.MAX_INPUT_BYTES) {
            throw new IllegalArgumentException("Please choose a document smaller than 20 MB.");
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        String content;
        String renderedHtml = "";
        String sourceFormat = "text";
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new IllegalArgumentException("The selected document could not be opened.");
            byte[] bytes = DocxSecurity.readLimited(input, DocxSecurity.MAX_INPUT_BYTES,
                    "Please choose a document smaller than 20 MB.");
            if (lower.endsWith(".docx")) {
                content = DocxTextExtractor.extract(new ByteArrayInputStream(bytes));
                renderedHtml = DocxHtmlExtractor.extract(new ByteArrayInputStream(bytes));
                sourceFormat = "docx";
            }
            else if (lower.endsWith(".txt") || lower.endsWith(".md"))
                content = DocxTextExtractor.readText(new ByteArrayInputStream(bytes));
            else throw new IllegalArgumentException("Please choose a DOCX, TXT or MD document.");
        }
        if (content.trim().isEmpty()) throw new IllegalArgumentException("No readable text was found in the selected document.");
        String title = fileName.replaceFirst("(?i)\\.(docx|txt|md)$", "")
                .replace('_', ' ').replaceAll("\\s+", " ").trim();
        return new DocumentImport(fileName, title, content, renderedHtml, sourceFormat);
    }

    private static String displayName(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.DISPLAY_NAME},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        }
        String last = uri.getLastPathSegment();
        return last == null ? "Imported document.docx" : last;
    }

    private static long displaySize(ContentResolver resolver, Uri uri) {
        try (Cursor cursor = resolver.query(uri, new String[]{OpenableColumns.SIZE},
                null, null, null)) {
            if (cursor != null && cursor.moveToFirst() && !cursor.isNull(0)) return cursor.getLong(0);
        } catch (Exception ignored) {}
        return -1;
    }
}
