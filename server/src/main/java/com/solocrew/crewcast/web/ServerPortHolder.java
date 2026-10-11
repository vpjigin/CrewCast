package com.solocrew.crewcast.web;

import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ServerPortHolder {

    private final AtomicInteger port = new AtomicInteger(-1);

    @EventListener
    public void onReady(WebServerInitializedEvent event) {
        port.set(event.getWebServer().getPort());
    }

    public int getPort() {
        return port.get();
    }

    public boolean isReady() {
        return port.get() > 0;
    }
}
