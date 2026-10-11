package com.solocrew.crewcast.web;

import org.springframework.http.ContentDisposition;

import java.nio.charset.StandardCharsets;

public final class ContentDispositionHelper {

    private ContentDispositionHelper() {
    }

    public static ContentDisposition attachmentFilename(String filename) {
        return ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
    }
}
