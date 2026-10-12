package com.solocrew.crewcast.media;

import java.util.Locale;

public record VideoFormat(
        int width,
        int height,
        double frameRate,
        String pixFmt,
        String profile,
        int bitDepth,
        String colorTransfer,
        String colorPrimaries) {

    public static VideoFormat unknown() {
        return new VideoFormat(0, 0, 0, "", "", 0, "", "");
    }

    public boolean isHdr() {
        String tr = colorTransfer == null ? "" : colorTransfer.toLowerCase(Locale.ROOT);
        String pr = colorPrimaries == null ? "" : colorPrimaries.toLowerCase(Locale.ROOT);
        return tr.contains("2084") || tr.contains("pq") || tr.contains("hlg")
                || pr.contains("bt2020");
    }
}
