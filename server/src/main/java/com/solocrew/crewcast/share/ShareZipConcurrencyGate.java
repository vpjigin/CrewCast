package com.solocrew.crewcast.share;

import java.util.concurrent.atomic.AtomicInteger;

/** Limits concurrent zip downloads. */
public final class ShareZipConcurrencyGate {

    private final int maxConcurrent;
    private final AtomicInteger active = new AtomicInteger(0);

    public ShareZipConcurrencyGate(int maxConcurrent) {
        this.maxConcurrent = Math.max(1, maxConcurrent);
    }

    public boolean tryEnter() {
        for (;;) {
            int current = active.get();
            if (current >= maxConcurrent) {
                return false;
            }
            if (active.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    public void exit() {
        active.updateAndGet(n -> Math.max(0, n - 1));
    }

    int activeCount() {
        return active.get();
    }
}
