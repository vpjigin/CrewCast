package com.solocrew.crewcast.desktop.ui;

import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.io.InputStream;
import java.util.EnumMap;
import java.util.Map;

public final class Fonts {

    public enum Weight {
        MEDIUM(500, "/fonts/Manrope-Medium.ttf"),
        SEMIBOLD(600, "/fonts/Manrope-SemiBold.ttf"),
        BOLD(700, "/fonts/Manrope-Bold.ttf"),
        EXTRABOLD(800, "/fonts/Manrope-ExtraBold.ttf");

        final int value;
        final String path;

        Weight(int value, String path) {
            this.value = value;
            this.path = path;
        }
    }

    private static final Map<Weight, Font> UI_BASE = new EnumMap<>(Weight.class);
    private static Font monoBase;
    private static boolean loaded;

    private Fonts() {
    }

    public static void load() throws IOException, FontFormatException {
        if (loaded) {
            return;
        }
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (Weight w : Weight.values()) {
            Font base = loadTtf(w.path);
            ge.registerFont(base);
            UI_BASE.put(w, base);
        }
        monoBase = loadTtf("/fonts/JetBrainsMono-Regular.ttf");
        ge.registerFont(monoBase);
        loaded = true;
    }

    public static boolean isLoaded() {
        return loaded;
    }

    /** UI font at weight 500 / 600 / 700 / 800 — never faked with {@link Font#BOLD}. */
    public static Font ui(int weight, float sizePt) {
        ensureLoaded();
        Weight w = fromNumeric(weight);
        return UI_BASE.get(w).deriveFont(Font.PLAIN, sizePt);
    }

    public static Font mono(float sizePt) {
        ensureLoaded();
        return monoBase.deriveFont(Font.PLAIN, sizePt);
    }

    private static Weight fromNumeric(int weight) {
        return switch (weight) {
            case 600 -> Weight.SEMIBOLD;
            case 700 -> Weight.BOLD;
            case 800 -> Weight.EXTRABOLD;
            default -> Weight.MEDIUM;
        };
    }

    private static void ensureLoaded() {
        if (!loaded) {
            try {
                load();
            } catch (Exception e) {
                throw new IllegalStateException("Bundled fonts not loaded", e);
            }
        }
    }

    private static Font loadTtf(String path) throws IOException, FontFormatException {
        try (InputStream in = Fonts.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IOException("Missing font: " + path);
            }
            return Font.createFont(Font.TRUETYPE_FONT, in);
        }
    }
}
