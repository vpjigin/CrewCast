package com.solocrew.crewcast.media;

import com.solocrew.crewcast.tunnel.CloudflaredPlatform;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class FfmpegDownloadAssetsTest {

    @Test
    void mapsWindowsToBtbNWin64Lgpl() {
        var plan = FfmpegDownloadAssets.planFor(new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.WINDOWS, CloudflaredPlatform.CpuArch.AMD64, "AMD64", "amd64", "test"));
        assertEquals(FfmpegDownloadAssets.Provider.BTBN_GITHUB, plan.provider());
        assertEquals("ffmpeg-master-latest-win64-lgpl.zip", plan.btbNArchiveName());
    }

    @Test
    void mapsLinuxArm64ToBtbNArmLgpl() {
        var plan = FfmpegDownloadAssets.planFor(new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.LINUX, CloudflaredPlatform.CpuArch.ARM64, "aarch64", "aarch64", "test"));
        assertEquals("ffmpeg-master-latest-linuxarm64-lgpl.tar.xz", plan.btbNArchiveName());
    }

    @Test
    void mapsDarwinArm64ToMartinRiedl() {
        var plan = FfmpegDownloadAssets.planFor(new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.DARWIN, CloudflaredPlatform.CpuArch.ARM64, "arm64", "aarch64", "test"));
        assertEquals(FfmpegDownloadAssets.Provider.MARTIN_RIEDL_MACOS, plan.provider());
        assertEquals("arm64", plan.macOsCpu());
    }

    @Test
    void downloaderPlanForCurrentHostIsNonNull() {
        assertNotNull(new FfmpegDownloader(null).planForCurrentHost().hostLabel());
    }
}
