package com.solocrew.crewcast.facade;

public record ViewerView(
        String key,
        String device,
        String fileName,
        boolean viaTunnel,
        Integer waitingProgress,
        String waitingDetail) {
}
