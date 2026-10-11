package com.solocrew.crewcast.desktop.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import java.awt.Color;
import java.net.URL;
import java.util.logging.Logger;

public final class Icons {

    private static final Logger LOG = Logger.getLogger(Icons.class.getName());

    private Icons() {
    }

    public static FlatSVGIcon of(String name, int size, Color color) {
        String resourcePath = "/icons/" + name + ".svg";
        URL url = Icons.class.getResource(resourcePath);
        if (url == null) {
            LOG.warning("Missing icon resource: " + resourcePath);
            url = Icons.class.getResource("/icons/missing.svg");
            if (url == null) {
                return new FlatSVGIcon("icons/missing.svg", size, size, Icons.class.getClassLoader())
                        .setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
            }
        }
        FlatSVGIcon icon = new FlatSVGIcon(url).derive(size, size);
        icon.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return icon;
    }

    public static FlatSVGIcon of(String name, Color color) {
        return of(name, 16, color);
    }
}
