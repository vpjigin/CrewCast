package com.solocrew.crewcast.desktop.ui;

import java.awt.Component;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public final class LayoutDebug {

    private static final boolean ENABLED =
            Boolean.getBoolean("crewcast.debugLayout");

    private LayoutDebug() {
    }

    public static void wire(String label, Component... components) {
        if (!ENABLED) {
            return;
        }
        for (Component c : components) {
            c.addComponentListener(new ComponentAdapter() {
                @Override
                public void componentResized(ComponentEvent e) {
                    logOne(label, c);
                }
            });
        }
        log(label, components);
    }

    public static void log(String label, Component... components) {
        if (!ENABLED) {
            return;
        }
        for (Component c : components) {
            logOne(label, c);
        }
    }

    private static void logOne(String label, Component c) {
        System.out.printf("[layout] %s bounds=%s size=%s%n", label, c.getBounds(), c.getSize());
    }
}
