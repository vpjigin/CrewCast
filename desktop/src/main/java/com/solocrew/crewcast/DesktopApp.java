package com.solocrew.crewcast;

import com.solocrew.crewcast.desktop.MainFrame;
import com.solocrew.crewcast.desktop.theme.CrewcastTheme;
import com.solocrew.crewcast.facade.SharingService;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import javax.swing.SwingUtilities;

public final class DesktopApp {

    private static volatile ConfigurableApplicationContext applicationContext;

    private DesktopApp() {
    }

    public static void main(String[] args) {
        com.solocrew.crewcast.system.CrewcastBootstrap.init();
        System.setProperty("java.awt.headless", "false");
        System.setProperty("apple.awt.application.name", "Crewcast");
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");

        SwingUtilities.invokeLater(() -> {
            CrewcastTheme.install();
            if (!CrewcastTheme.usedBundledFonts()) {
                System.err.println("Crewcast: bundled fonts unavailable; using system UI font.");
            }
            MainFrame frame = new MainFrame();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            Thread springThread = new Thread(() -> {
                ConfigurableApplicationContext ctx = new SpringApplicationBuilder(CrewcastApplication.class)
                        .properties("server.port=0", "server.address=0.0.0.0")
                        .headless(true)
                        .run(args);
                applicationContext = ctx;
                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    ctx.getBean(SharingService.class).shutdown();
                    ctx.close();
                }, "shutdown-hook"));
                SwingUtilities.invokeLater(() -> frame.onSpringReady(ctx));
            }, "spring-boot");
            springThread.setDaemon(false);
            springThread.start();
        });
    }

    public static ConfigurableApplicationContext getApplicationContext() {
        return applicationContext;
    }
}
