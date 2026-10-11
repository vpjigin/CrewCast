package com.solocrew.crewcast.media;

final class SrtToWebVttConverter {

    private SrtToWebVttConverter() {
    }

    static String convert(String srtText) {
        String normalized = srtText.replace("\r\n", "\n").replace('\r', '\n');
        StringBuilder out = new StringBuilder("WEBVTT\n\n");
        for (String line : normalized.split("\n", -1)) {
            if (line.contains("-->")) {
                out.append(line.replace(',', '.')).append('\n');
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    static String ensureWebVttHeader(String text) {
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        if (t.startsWith("WEBVTT")) {
            return t;
        }
        return "WEBVTT\n\n" + t;
    }
}
