package com.solocrew.crewcast.media;

/**
 * Parses ffmpeg {@code -progress pipe:1} lines into progress, speed, and ETA.
 */
public final class FfmpegProgressParser {

    private FfmpegProgressParser() {
    }

    public static final class Snapshot {
        public int progress;
        public Double speed;
        public Integer etaSeconds;

        public Snapshot(int progress, Double speed, Integer etaSeconds) {
            this.progress = progress;
            this.speed = speed;
            this.etaSeconds = etaSeconds;
        }
    }

    public static Snapshot applyLine(String line, double durationSec, int currentProgress) {
        if (line == null) {
            return new Snapshot(currentProgress, null, null);
        }
        Double speed = null;
        Integer eta = null;
        int progress = currentProgress;
        if (line.startsWith("out_time_us=")) {
            long us = parseLongSuffix(line);
            if (durationSec > 0) {
                progress = (int) Math.min(99, (us / 1_000_000.0) / durationSec * 100);
                progress = Math.max(currentProgress, progress);
            }
        } else if (line.startsWith("progress=end")) {
            progress = 100;
        } else if (line.startsWith("speed=")) {
            speed = parseSpeed(line);
            if (speed != null && speed > 0 && durationSec > 0 && progress < 100) {
                double outSec = (progress / 100.0) * durationSec;
                double remaining = Math.max(0, durationSec - outSec);
                eta = (int) Math.ceil(remaining / speed);
            }
        }
        return new Snapshot(progress, speed, eta);
    }

    static double parseSpeed(String line) {
        String raw = line.substring(line.indexOf('=') + 1).trim();
        if (raw.endsWith("x")) {
            raw = raw.substring(0, raw.length() - 1).trim();
        }
        if (raw.isEmpty() || "N/A".equalsIgnoreCase(raw)) {
            return 0;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static long parseLongSuffix(String line) {
        int i = line.indexOf('=');
        if (i < 0) {
            return 0;
        }
        try {
            return Long.parseLong(line.substring(i + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
