package com.solocrew.crewcast.media;

import com.solocrew.crewcast.settings.SettingsStore;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

@Component
public class FfmpegBootstrap {

    private final FfmpegManager ffmpegManager;
    private final SettingsStore settingsStore;

    public FfmpegBootstrap(FfmpegManager ffmpegManager, SettingsStore settingsStore) {
        this.ffmpegManager = ffmpegManager;
        this.settingsStore = settingsStore;
    }

    @PostConstruct
    void applyDefaults() {
        if (!settingsStore.settingsFileExistedBeforeLoad() && ffmpegManager.isAvailable()) {
            var s = settingsStore.getSettings();
            s.setFixAudioForBrowsers(true);
            settingsStore.save();
        }
    }
}
