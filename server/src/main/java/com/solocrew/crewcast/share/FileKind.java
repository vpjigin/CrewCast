package com.solocrew.crewcast.share;

public enum FileKind {
    VIDEO("Video"),
    IMAGE("Image"),
    DOCUMENT("Document"),
    ARCHIVE("Archive"),
    AUDIO("Audio"),
    SPREADSHEET("Spreadsheet"),
    OTHER("Other");

    private final String label;

    FileKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static FileKind forFileName(String name) {
        String lower = name.toLowerCase();
        int dot = lower.lastIndexOf('.');
        if (dot < 0 || dot == lower.length() - 1) {
            return OTHER;
        }
        String ext = lower.substring(dot + 1);
        if (ShareRegistry.isVideoFileName(name)) {
            return VIDEO;
        }
        return switch (ext) {
            case "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif", "avif", "tif", "tiff" -> IMAGE;
            case "pdf", "doc", "docx", "txt", "rtf", "odt", "md", "pages" -> DOCUMENT;
            case "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "tgz", "tbz" -> ARCHIVE;
            case "mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "wma", "aiff" -> AUDIO;
            case "xls", "xlsx", "csv", "ods", "numbers" -> SPREADSHEET;
            default -> OTHER;
        };
    }

    public static String extensionLabel(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "FILE";
        }
        return name.substring(dot + 1).toUpperCase();
    }

    public boolean viewableInBrowser() {
        return this == VIDEO || this == IMAGE;
    }
}
