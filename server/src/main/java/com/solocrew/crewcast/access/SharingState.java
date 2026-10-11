package com.solocrew.crewcast.access;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class SharingState {

    private final AtomicBoolean sharingActive = new AtomicBoolean(false);

    public boolean isSharingActive() {
        return sharingActive.get();
    }

    public void setSharingActive(boolean active) {
        sharingActive.set(active);
    }
}
