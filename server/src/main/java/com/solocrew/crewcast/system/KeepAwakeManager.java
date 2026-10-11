package com.solocrew.crewcast.system;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class KeepAwakeManager {

    private Process process;

    public synchronized void start() {
        stop();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();
        if (os.contains("mac")) {
            cmd.add("caffeinate");
            cmd.add("-dis");
        } else if (os.contains("linux")) {
            if (!commandExists("systemd-inhibit")) {
                return;
            }
            cmd.add("systemd-inhibit");
            cmd.add("--what=idle:sleep");
            cmd.add("sleep");
            cmd.add("infinity");
        } else if (os.contains("win")) {
            // Best effort: no reliable cross-JDK keep-awake without native APIs.
            return;
        } else {
            return;
        }
        try {
            process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        } catch (IOException ignored) {
            process = null;
        }
    }

    public synchronized void stop() {
        if (process != null) {
            process.destroyForcibly();
            process = null;
        }
    }

    public synchronized boolean isActive() {
        return process != null && process.isAlive();
    }

    private static boolean commandExists(String name) {
        String path = System.getenv("PATH");
        if (path == null) {
            return false;
        }
        for (String dir : path.split(java.io.File.pathSeparator)) {
            if (new java.io.File(dir, name).canExecute()) {
                return true;
            }
        }
        return false;
    }
}
