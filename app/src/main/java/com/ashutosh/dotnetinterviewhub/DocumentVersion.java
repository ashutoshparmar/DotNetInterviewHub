package com.ashutosh.dotnetinterviewhub;

public class DocumentVersion {
    public final long id;
    public final long documentId;
    public final String title;
    public final String sourceName;
    public final long savedAt;

    public DocumentVersion(long id, long documentId, String title, String sourceName, long savedAt) {
        this.id = id;
        this.documentId = documentId;
        this.title = title;
        this.sourceName = sourceName;
        this.savedAt = savedAt;
    }
}
