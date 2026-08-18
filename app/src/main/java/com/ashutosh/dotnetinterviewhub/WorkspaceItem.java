package com.ashutosh.dotnetinterviewhub;

public class WorkspaceItem {
    public final long id;
    public final String name;
    public final String description;

    public WorkspaceItem(long id, String name, String description) {
        this.id = id;
        this.name = name;
        this.description = description;
    }

    @Override
    public String toString() {
        return name;
    }
}
