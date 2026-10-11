package com.solocrew.crewcast.tunnel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CloudflaredPlatformTest {

    @Test
    void darwinArm64UnameMapsToArm64Asset() {
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.DARWIN,
                CloudflaredPlatform.CpuArch.ARM64,
                "arm64",
                "aarch64",
                "test");
        assertEquals("cloudflared-darwin-arm64.tgz", CloudflaredPlatform.assetName(info));
    }

    @Test
    void darwinAmd64UnameMapsToAmd64Asset() {
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.DARWIN,
                CloudflaredPlatform.CpuArch.AMD64,
                "x86_64",
                "x86_64",
                "test");
        assertEquals("cloudflared-darwin-amd64.tgz", CloudflaredPlatform.assetName(info));
    }

    @Test
    void darwinX86UnameWithAppleSiliconHwFlagMapsToArm64Asset() {
        var resolved = CloudflaredPlatform.resolveDarwinArch("x86_64", "1", "x86_64");
        assertEquals(CloudflaredPlatform.CpuArch.ARM64, resolved.arch());
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.DARWIN,
                resolved.arch(),
                "x86_64",
                "x86_64",
                resolved.detail());
        assertEquals("cloudflared-darwin-arm64.tgz", CloudflaredPlatform.assetName(info));
    }

    @Test
    void darwinX86UnameWithoutAppleSiliconStaysAmd64() {
        var resolved = CloudflaredPlatform.resolveDarwinArch("x86_64", "0", "x86_64");
        assertEquals(CloudflaredPlatform.CpuArch.AMD64, resolved.arch());
        assertEquals(
                "cloudflared-darwin-amd64.tgz",
                CloudflaredPlatform.assetName(new CloudflaredPlatform.Info(
                        CloudflaredPlatform.OsKind.DARWIN,
                        resolved.arch(),
                        "x86_64",
                        "x86_64",
                        resolved.detail())));
    }

    @Test
    void linuxAarch64MapsToLinuxArm64Binary() {
        assertEquals(CloudflaredPlatform.CpuArch.ARM64, CloudflaredPlatform.parseUnameMachine("aarch64"));
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.LINUX,
                CloudflaredPlatform.CpuArch.ARM64,
                "aarch64",
                "aarch64",
                "uname -m=aarch64");
        assertEquals("cloudflared-linux-arm64", CloudflaredPlatform.assetName(info));
    }

    @Test
    void linuxX86_64MapsToLinuxAmd64Binary() {
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.LINUX,
                CloudflaredPlatform.CpuArch.AMD64,
                "x86_64",
                "amd64",
                "uname -m=x86_64");
        assertEquals("cloudflared-linux-amd64", CloudflaredPlatform.assetName(info));
    }

    @Test
    void windowsAmd64EnvMapsToWindowsAmd64Exe() {
        assertEquals(
                CloudflaredPlatform.CpuArch.AMD64,
                CloudflaredPlatform.archFromWindowsEnv("AMD64", null));
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.WINDOWS,
                CloudflaredPlatform.CpuArch.AMD64,
                "AMD64",
                "amd64",
                "env");
        assertEquals("cloudflared-windows-amd64.exe", CloudflaredPlatform.assetName(info));
    }

    @Test
    void windowsX86ProcessOn64BitHostUsesWow64Env() {
        assertEquals(
                CloudflaredPlatform.CpuArch.AMD64,
                CloudflaredPlatform.archFromWindowsEnv("x86", "AMD64"));
        assertEquals(
                CloudflaredPlatform.CpuArch.ARM64,
                CloudflaredPlatform.archFromWindowsEnv("x86", "ARM64"));
    }

    @Test
    void windows386MapsTo386Exe() {
        assertEquals(
                CloudflaredPlatform.CpuArch.I386,
                CloudflaredPlatform.archFromWindowsEnv("x86", null));
        var info = new CloudflaredPlatform.Info(
                CloudflaredPlatform.OsKind.WINDOWS,
                CloudflaredPlatform.CpuArch.I386,
                "x86",
                "x86",
                "env");
        assertEquals("cloudflared-windows-386.exe", CloudflaredPlatform.assetName(info));
    }

    @Test
    void detectOnCurrentHostProducesKnownAssetName() {
        CloudflaredPlatform.Info info = CloudflaredPlatform.detect();
        String asset = CloudflaredPlatform.assetName(info);
        assertEquals(true, asset.startsWith("cloudflared-"));
    }
}
