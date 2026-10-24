package com.solocrew.crewcast.media;

/**
 * Parses ffmpeg {@code -progress pipe:1} lines into progress, speed, and ETA.
 * Note: ffmpeg's {@code out_time_ms} is misnamed — values are in microseconds, same as {@code out_time_us}.
 */
public final class FfmpegProgressParser {

    private FfmpegProgressParser() {
    }

    public static final class ProgressState {
        public int progress;
        public Double speed;
        public Integer etaSeconds;
        public boolean finishing;

        public ProgressState(int progress) {
            this.progress = progress;
        }
    }

    public static final class Snapshot {
        public final int progress;
        public final Double speed;
        public final Integer etaSeconds;
        public final boolean finishing;

        public Snapshot(int progress, Double speed, Integer etaSeconds, boolean finishing) {
            this.progress = progress;
            this.speed = speed;
            this.etaSeconds = etaSeconds;
            this.finishing = finishing;
        }
    }

    /** @deprecated use {@link #applyLine(String, double, ProgressState)} */
    public static Snapshot applyLine(String line, double durationSec, int currentProgress) {
        ProgressState state = new ProgressState(currentProgress);
        return applyLine(line, durationSec, state);
    }

    public static Snapshot applyLine(String line, double durationSec, ProgressState state) {
        if (line == null || state == null) {
            return new Snapshot(state != null ? state.progress : 0, null, null, false);
        }
        Double speed = null;
        Integer eta = null;
        int progress = state.progress;
        boolean finishing = state.finishing;
        if (line.startsWith("out_time_us=")) {
            progress = progressFromMicros(parseLongSuffix(line), durationSec, progress);
        } else if (line.startsWith("out_time_ms=")) {
            progress = progressFromMicros(parseLongSuffix(line), durationSec, progress);
        } else if (line.startsWith("out_time=")) {
            progress = progressFromSeconds(parseOutTimeSeconds(line), durationSec, progress);
        } else if (line.startsWith("progress=end")) {
            progress = 99;
            finishing = true;
        } else if (line.startsWith("speed=")) {
            speed = parseSpeed(line);
        }
        Double speedForEta = speed != null ? speed : state.speed;
        int progressForEta = progress;
        if (speedForEta != null && speedForEta > 0 && durationSec > 0 && progressForEta < 99 && !finishing) {
            double outSec = (progressForEta / 100.0) * durationSec;
            double remaining = Math.max(0, durationSec - outSec);
            eta = (int) Math.ceil(remaining / speedForEta);
        }
        state.progress = progress;
        state.finishing = finishing;
        if (speed != null && speed > 0) {
            state.speed = speed;
        }
        return new Snapshot(progress, speed, eta, finishing);
    }

    static int progressFromMicros(long micros, double durationSec, int currentProgress) {
        if (durationSec <= 0 || micros < 0) {
            return currentProgress;
        }
        int pct = (int) Math.floor((micros / 1_000_000.0) / durationSec * 100.0);
        pct = Math.max(0, Math.min(99, pct));
        return Math.max(currentProgress, pct);
    }

    static int progressFromSeconds(double outSec, double durationSec, int currentProgress) {
        if (durationSec <= 0 || outSec < 0) {
            return currentProgress;
        }
        int pct = (int) Math.floor(outSec / durationSec * 100.0);
        pct = Math.max(0, Math.min(99, pct));
        return Math.max(currentProgress, pct);
    }

    /**
     * Parses {@code out_time=HH:MM:SS.micro} (micro may be 1–6 digits).
     */
    static double parseOutTimeSeconds(String line) {
        int eq = line.indexOf('=');
        if (eq < 0) {
            return -1;
        }
        String raw = line.substring(eq + 1).trim();
        if (raw.isEmpty()) {
            return -1;
        }
        String[] parts = raw.split(":");
        if (parts.length != 3) {
            return -1;
        }
        try {
            int hours = Integer.parseInt(parts[0]);
            int minutes = Integer.parseInt(parts[1]);
            String secPart = parts[2];
            double seconds;
            if (secPart.contains(".")) {
                seconds = Double.parseDouble(secPart);
            } else {
                seconds = Integer.parseInt(secPart);
            }
            return hours * 3600.0 + minutes * 60.0 + seconds;
        } catch (NumberFormatException e) {
            return -1;
        }
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

    static long parseLongSuffix(String line) {
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
