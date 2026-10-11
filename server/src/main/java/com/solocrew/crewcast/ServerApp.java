package com.solocrew.crewcast;

import com.solocrew.crewcast.facade.SharingLinks;
import com.solocrew.crewcast.facade.SharingService;
import com.solocrew.crewcast.facade.SharingUiListener;
import com.solocrew.crewcast.facade.TunnelState;
import com.solocrew.crewcast.system.CrewcastBootstrap;
import com.solocrew.crewcast.system.CrewcastDiagnostics;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

public final class ServerApp {

    private ServerApp() {
    }

    public static void main(String[] args) throws Exception {
        CrewcastBootstrap.init();
        List<String> sharePaths = new ArrayList<>();
        Integer port = null;
        boolean noTunnel = false;
        boolean downloadFfmpeg = false;
        boolean diagnose = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--diagnose" -> diagnose = true;
                case "--download-ffmpeg" -> downloadFfmpeg = true;
                case "--share" -> {
                    if (i + 1 < args.length) {
                        sharePaths.add(args[++i]);
                    }
                }
                case "--port" -> {
                    if (i + 1 < args.length) {
                        port = Integer.parseInt(args[++i]);
                    }
                }
                case "--no-tunnel" -> noTunnel = true;
                default -> {
                }
            }
        }

        if (diagnose) {
            System.out.println(CrewcastDiagnostics.runReport());
            return;
        }

        if (sharePaths.isEmpty()) {
            System.err.println(
                    "Usage: ServerApp --share <path> [--share <path> ...] [--port N] [--no-tunnel] [--diagnose]");
            System.exit(1);
        }

        int serverPort = port != null ? port : 0;
        ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CrewcastApplication.class)
                .properties("server.port=" + serverPort, "server.address=0.0.0.0")
                .headless(true)
                .run();

        SharingService sharing = ctx.getBean(SharingService.class);
        if (downloadFfmpeg) {
            System.out.println("Downloading ffmpeg (consented via --download-ffmpeg)…");
            sharing.downloadFfmpegBlocking();
            if (!sharing.isFfmpegAvailable()) {
                System.err.println("ffmpeg download failed.");
                ctx.close();
                System.exit(1);
            }
            System.out.println("ffmpeg installed under crewcast home bin/");
        }
        if (noTunnel) {
            sharing.getSettings().setStartTunnelWhenSharingStarts(false);
            sharing.saveSettings(sharing.getSettings());
        }

        for (String raw : sharePaths) {
            Path p = Path.of(raw).toAbsolutePath().normalize();
            if (!Files.exists(p)) {
                System.err.println("Skipping missing path: " + p);
                continue;
            }
            if (Files.isDirectory(p)) {
                sharing.addFolder(p);
            } else {
                sharing.addFile(p);
            }
        }

        if (!sharing.hasShares()) {
            System.err.println("No valid shares added.");
            ctx.close();
            System.exit(1);
        }

        CountDownLatch tunnelReady = new CountDownLatch(1);
        sharing.addListener(new SharingUiListener() {
            @Override
            public void onTunnelUrl(String url) {
                tunnelReady.countDown();
                printLinks(sharing);
            }

            @Override
            public void onTunnelError(String message, boolean retryable) {
                if (!retryable) {
                    tunnelReady.countDown();
                }
                System.err.println("Tunnel: " + message);
            }
        });

        sharing.startSharing();

        while (!sharing.isServerReady()) {
            Thread.sleep(50);
        }
        printLinks(sharing);

        if (!noTunnel && sharing.getSettings().isStartTunnelWhenSharingStarts()) {
            new Thread(() -> {
                try {
                    tunnelReady.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }, "tunnel-wait").start();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sharing.shutdown();
            ctx.close();
        }, "server-shutdown"));

        System.out.println("Crewcast headless server running. Press Ctrl+C to stop.");
        Thread.currentThread().join();
    }

    private static void printLinks(SharingService sharing) {
        SharingLinks links = sharing.currentLinks();
        if (!links.lanLink().isBlank()) {
            System.out.println("LAN:    " + links.lanLink());
        }
        if (!links.publicLink().isBlank()) {
            System.out.println("Public: " + links.publicLink());
        } else if (sharing.getTunnelState() != TunnelState.CONNECTED) {
            System.out.println("Public: (waiting for tunnel…)");
        }
    }
}
