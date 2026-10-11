package com.solocrew.crewcast.media;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Unified embedded + sidecar subtitle tracks for one video file. */
public final class SubtitleCatalog {

    private static final Pattern LANG_SUFFIX = Pattern.compile("^[a-zA-Z]{2,3}$");

    private SubtitleCatalog() {
    }

    public enum Source {
        EMBEDDED,
        EXTERNAL
    }

    public record Track(
            int index,
            String language,
            String languageName,
            String label,
            Source source,
            boolean supported,
            int embeddedSubtitleIndex,
            Path externalPath) {

        public Track {
            if (source == Source.EMBEDDED) {
                externalPath = null;
            }
        }
    }

    public static List<Track> build(Path video, ProbedMedia media) {
        List<Track> out = new ArrayList<>();
        int nextIndex = 0;
        for (SubtitleStreamInfo sub : media.subtitleTracks()) {
            String lang = sub.languageTag();
            String languageName = LanguageNames.subtitleDisplayName(lang, sub.subtitleIndex());
            String label = buildLabel(languageName, sub.title());
            out.add(new Track(
                    nextIndex++,
                    lang,
                    languageName,
                    label,
                    Source.EMBEDDED,
                    sub.supported(),
                    sub.subtitleIndex(),
                    null));
        }
        for (Path sidecar : discoverSidecars(video)) {
            String lang = languageFromSidecar(video, sidecar);
            int subNum = out.size();
            String languageName = LanguageNames.subtitleDisplayName(lang, subNum);
            String label = buildLabel(languageName, "");
            out.add(new Track(
                    nextIndex++,
                    lang,
                    languageName,
                    label,
                    Source.EXTERNAL,
                    true,
                    -1,
                    sidecar));
        }
        return List.copyOf(out);
    }

    private static String buildLabel(String languageName, String title) {
        if (title != null && !title.isBlank()) {
            return languageName + " — " + title.trim();
        }
        return languageName;
    }

    static List<Path> discoverSidecars(Path video) {
        Path dir = video.getParent();
        if (dir == null) {
            return List.of();
        }
        String base = stripExtension(video.getFileName().toString());
        List<Path> found = new ArrayList<>();
        addIfExists(found, dir.resolve(base + ".srt"));
        addIfExists(found, dir.resolve(base + ".vtt"));
        try (var stream = java.nio.file.Files.newDirectoryStream(dir, base + ".*")) {
            for (Path p : stream) {
                String name = p.getFileName().toString();
                if (!name.startsWith(base + ".")) {
                    continue;
                }
                String rest = name.substring(base.length() + 1);
                int dot = rest.lastIndexOf('.');
                if (dot <= 0) {
                    continue;
                }
                String langPart = rest.substring(0, dot);
                String ext = rest.substring(dot + 1).toLowerCase(Locale.ROOT);
                if (("srt".equals(ext) || "vtt".equals(ext)) && LANG_SUFFIX.matcher(langPart).matches()) {
                    addIfExists(found, p);
                }
            }
        } catch (Exception ignored) {
        }
        return found;
    }

    private static void addIfExists(List<Path> found, Path p) {
        if (java.nio.file.Files.isRegularFile(p) && found.stream().noneMatch(x -> x.equals(p))) {
            found.add(p.normalize());
        }
    }

    private static String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String languageFromSidecar(Path video, Path sidecar) {
        String base = stripExtension(video.getFileName().toString());
        String file = sidecar.getFileName().toString();
        if (file.equalsIgnoreCase(base + ".srt") || file.equalsIgnoreCase(base + ".vtt")) {
            return "";
        }
        String rest = file.substring(base.length() + 1);
        int dot = rest.lastIndexOf('.');
        if (dot > 0) {
            return rest.substring(0, dot).toLowerCase(Locale.ROOT);
        }
        return "";
    }
}
