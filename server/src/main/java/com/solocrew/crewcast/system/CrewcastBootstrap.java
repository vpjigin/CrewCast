package com.solocrew.crewcast.system;

/** Runs before Spring or desktop UI so logging, TLS, and proxies are configured early. */
public final class CrewcastBootstrap {

    private static volatile boolean done;

    private CrewcastBootstrap() {}

    public static void init() {
        if (done) {
            return;
        }
        synchronized (CrewcastBootstrap.class) {
            if (done) {
                return;
            }
            TrustStoreBootstrap.enableSystemProxies();
            TrustStoreBootstrap.configureEarly();
            CrewcastLogging.installIfNeeded();
            done = true;
        }
    }
}
