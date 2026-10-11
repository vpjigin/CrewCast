package com.solocrew.crewcast.tunnel;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/** Host OS/CPU detection for choosing the official cloudflared GitHub release asset. */
public final class CloudflaredPlatform {

    public enum OsKind {
        DARWIN,
        LINUX,
        WINDOWS,
        OTHER
    }

    public enum CpuArch {
        ARM64,
        AMD64,
        I386,
        UNKNOWN
    }

    public record Info(OsKind os, CpuArch arch, String rawMachine, String jvmOsArch, String detail) {
    }

    private CloudflaredPlatform() {
    }

    public static Info detect() {
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String jvmArch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (osName.contains("mac") || osName.contains("darwin")) {
            return detectDarwin(jvmArch);
        }
        if (osName.contains("linux")) {
            return detectLinux(jvmArch);
        }
        if (osName.contains("win")) {
            return detectWindows(jvmArch);
        }
        CpuArch arch = archFromJvm(jvmArch);
        return new Info(OsKind.OTHER, arch, jvmArch, jvmArch, "fallback JVM os.arch");
    }

    static Info detectDarwin(String jvmArch) {
        String uname = firstLine(runQuiet("uname", "-m"));
        String raw = uname != null && !uname.isBlank() ? uname.trim() : jvmArch;
        String hwArm = firstLine(runQuiet("sysctl", "-n", "hw.optional.arm64"));
        ResolvedDarwin resolved = resolveDarwinArch(raw, hwArm, jvmArch);
        return new Info(OsKind.DARWIN, resolved.arch(), raw, jvmArch, resolved.detail());
    }

    record ResolvedDarwin(CpuArch arch, String detail) {
    }

    static ResolvedDarwin resolveDarwinArch(String unameMachine, String hwOptionalArm64, String jvmArch) {
        String raw = unameMachine != null ? unameMachine.trim() : "";
        CpuArch arch = parseUnameMachine(raw);
        String detail = "uname -m=" + (raw.isEmpty() ? jvmArch : raw);
        if (arch == CpuArch.AMD64 && "x86_64".equalsIgnoreCase(raw)) {
            String hw = hwOptionalArm64 != null ? hwOptionalArm64.trim() : "";
            detail += ", hw.optional.arm64=" + (hw.isEmpty() ? "?" : hw);
            if ("1".equals(hw)) {
                arch = CpuArch.ARM64;
                detail += " (Apple Silicon host, x86_64 JVM/Rosetta)";
            }
        }
        if (arch == CpuArch.UNKNOWN) {
            arch = archFromJvm(jvmArch);
            detail += ", fallback JVM os.arch=" + jvmArch;
        }
        return new ResolvedDarwin(arch, detail);
    }

    static Info detectLinux(String jvmArch) {
        String uname = firstLine(runQuiet("uname", "-m"));
        String raw = uname != null && !uname.isBlank() ? uname.trim() : jvmArch;
        CpuArch arch = parseUnameMachine(raw);
        if (arch == CpuArch.UNKNOWN) {
            arch = archFromJvm(jvmArch);
            return new Info(OsKind.LINUX, arch, raw, jvmArch, "uname -m=" + raw + ", fallback JVM os.arch=" + jvmArch);
        }
        return new Info(OsKind.LINUX, arch, raw, jvmArch, "uname -m=" + raw);
    }

    static Info detectWindows(String jvmArch) {
        String procArch = env("PROCESSOR_ARCHITECTURE");
        String procArchWow = env("PROCESSOR_ARCHITEW6432");
        CpuArch arch = archFromWindowsEnv(procArch, procArchWow);
        if (arch == CpuArch.UNKNOWN) {
            arch = archFromJvm(jvmArch);
        }
        String raw = procArch != null ? procArch : jvmArch;
        String detail = "PROCESSOR_ARCHITECTURE="
                + nullDash(procArch)
                + ", PROCESSOR_ARCHITEW6432="
                + nullDash(procArchWow);
        if (arch == archFromJvm(jvmArch) && arch != CpuArch.UNKNOWN) {
            detail += ", JVM os.arch=" + jvmArch;
        }
        return new Info(OsKind.WINDOWS, arch, raw, jvmArch, detail);
    }

    /** Maps detected host platform to GitHub release asset file name. */
    public static String assetName(Info platform) {
        return switch (platform.os()) {
            case DARWIN -> platform.arch() == CpuArch.ARM64
                    ? "cloudflared-darwin-arm64.tgz"
                    : "cloudflared-darwin-amd64.tgz";
            case LINUX -> platform.arch() == CpuArch.ARM64
                    ? "cloudflared-linux-arm64"
                    : "cloudflared-linux-amd64";
            case WINDOWS -> platform.arch() == CpuArch.I386
                    ? "cloudflared-windows-386.exe"
                    : "cloudflared-windows-amd64.exe";
            case OTHER -> platform.arch() == CpuArch.ARM64
                    ? "cloudflared-linux-arm64"
                    : "cloudflared-linux-amd64";
        };
    }

    static CpuArch parseUnameMachine(String machine) {
        if (machine == null || machine.isBlank()) {
            return CpuArch.UNKNOWN;
        }
        String m = machine.trim().toLowerCase(Locale.ROOT);
        if (m.equals("arm64") || m.equals("aarch64")) {
            return CpuArch.ARM64;
        }
        if (m.equals("x86_64") || m.equals("amd64")) {
            return CpuArch.AMD64;
        }
        if (m.startsWith("arm") || m.equals("armv7l") || m.equals("armv8l")) {
            return CpuArch.ARM64;
        }
        if (m.equals("i386") || m.equals("i686")) {
            return CpuArch.I386;
        }
        return CpuArch.UNKNOWN;
    }

    static CpuArch archFromWindowsEnv(String processorArchitecture, String processorArchiteW6432) {
        String wow = processorArchiteW6432 != null ? processorArchiteW6432.trim().toUpperCase(Locale.ROOT) : "";
        String arch = processorArchitecture != null ? processorArchitecture.trim().toUpperCase(Locale.ROOT) : "";
        if ("ARM64".equals(wow) || "ARM64".equals(arch)) {
            return CpuArch.ARM64;
        }
        if ("AMD64".equals(wow) || "AMD64".equals(arch)) {
            return CpuArch.AMD64;
        }
        if ("X86".equals(arch) || "I386".equals(arch)) {
            return CpuArch.I386;
        }
        return CpuArch.UNKNOWN;
    }

    static CpuArch archFromJvm(String jvmArch) {
        if (jvmArch == null || jvmArch.isBlank()) {
            return CpuArch.UNKNOWN;
        }
        String a = jvmArch.toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.equals("arm64")) {
            return CpuArch.ARM64;
        }
        if (a.contains("amd64") || a.contains("x86_64")) {
            return CpuArch.AMD64;
        }
        if (a.contains("86") || a.equals("i386")) {
            return CpuArch.I386;
        }
        if (a.contains("arm")) {
            return CpuArch.ARM64;
        }
        return CpuArch.UNKNOWN;
    }

    private static String env(String name) {
        return System.getenv(name);
    }

    private static String nullDash(String v) {
        return v == null || v.isBlank() ? "-" : v;
    }

    private static String firstLine(String output) {
        if (output == null || output.isBlank()) {
            return null;
        }
        return output.lines().findFirst().orElse("").trim();
    }

    private static String runQuiet(String... command) {
        try {
            Process p = new ProcessBuilder(command).redirectErrorStream(true).start();
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                return null;
            }
            if (p.exitValue() != 0) {
                return null;
            }
            return new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }
}
