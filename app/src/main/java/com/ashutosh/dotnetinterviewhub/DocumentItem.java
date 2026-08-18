package com.ashutosh.dotnetinterviewhub;

public class DocumentItem {
    public long id;
    public String title;
    public String category;
    public String content;
    public String sourceName;
    public long updatedAt;
    public boolean seeded;
    public boolean bookmarked;
    public long workspaceId;
    public String workspaceName;
    public String folderName;
    public String tags;
    public long lastOpenedAt;
    public int readingProgress;

    public DocumentItem() {}

    public DocumentItem(long id, String title, String category, String content,
                        String sourceName, long updatedAt, boolean seeded, boolean bookmarked) {
        this.id = id;
        this.title = title;
        this.category = category;
        this.content = content;
        this.sourceName = sourceName;
        this.updatedAt = updatedAt;
        this.seeded = seeded;
        this.bookmarked = bookmarked;
    }
}
