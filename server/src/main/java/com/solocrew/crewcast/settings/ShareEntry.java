package com.solocrew.crewcast.settings;

public class ShareEntry {

    public enum Kind {
        FOLDER, FILE
    }

    private String path;
    private Kind kind;

    public ShareEntry() {
    }

    public ShareEntry(String path, Kind kind) {
        this.path = path;
        this.kind = kind;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Kind getKind() {
        return kind;
    }

    public void setKind(Kind kind) {
        this.kind = kind;
    }
}
