package com.solocrew.crewcast.system;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/** On Windows packaged builds, prefer the OS certificate store when jdk.crypto.mscapi is present. */
public final class TrustStoreBootstrap {

    private static final Logger log = LoggerFactory.getLogger(TrustStoreBootstrap.class);
    private static volatile String activeTrustStore = "Java default";

    private TrustStoreBootstrap() {}

    public static void configureEarly() {
        if (!isWindows()) {
            activeTrustStore = "Java default";
            return;
        }
        if (Boolean.getBoolean("crewcast.useJavaTrustStore")) {
            activeTrustStore = "Java default (crewcast.useJavaTrustStore=true)";
            log.info("Trust store: {}", activeTrustStore);
            return;
        }
        if (!modulePresent("jdk.crypto.mscapi")) {
            activeTrustStore = "Java default (jdk.crypto.mscapi not in runtime)";
            log.info("Trust store: {}", activeTrustStore);
            return;
        }
        System.setProperty("javax.net.ssl.trustStoreType", "Windows-ROOT");
        activeTrustStore = "Windows-ROOT (OS certificate store)";
        log.info("Trust store: {}", activeTrustStore);
    }

    public static String activeTrustStoreLabel() {
        return activeTrustStore;
    }

    public static void enableSystemProxies() {
        System.setProperty("java.net.useSystemProxies", "true");
    }

    private static boolean modulePresent(String name) {
        return ModuleLayer.boot().modules().stream().anyMatch(m -> m.getName().equals(name));
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }
}
