package com.solocrew.crewcast.system;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.rolling.FixedWindowRollingPolicy;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.util.FileSize;
import ch.qos.logback.core.rolling.SizeBasedTriggeringPolicy;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.util.regex.Pattern;

/** Installs rotating file logging under {@link CrewcastPaths#logsDir()}. */
public final class CrewcastLogging {

    private static final Pattern ACCESS_KEY = Pattern.compile("([?&]t=)[^&\\s\"']+", Pattern.CASE_INSENSITIVE);
    private static volatile boolean installed;

    private CrewcastLogging() {}

    public static void installIfNeeded() {
        if (installed) {
            return;
        }
        synchronized (CrewcastLogging.class) {
            if (installed) {
                return;
            }
            try {
                PathUtil.ensureDir(CrewcastPaths.logsDir());
                LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
                RollingFileAppender<ILoggingEvent> fileAppender = new RollingFileAppender<>();
                fileAppender.setContext(ctx);
                fileAppender.setName("CREWCAST_FILE");
                fileAppender.setFile(CrewcastPaths.logFile().toString());
                fileAppender.setAppend(true);

                FixedWindowRollingPolicy rolling = new FixedWindowRollingPolicy();
                rolling.setContext(ctx);
                rolling.setParent(fileAppender);
                rolling.setFileNamePattern(CrewcastPaths.logsDir().resolve("crewcast.%i.log").toString());
                rolling.setMinIndex(1);
                rolling.setMaxIndex(5);
                rolling.start();

                SizeBasedTriggeringPolicy<ILoggingEvent> trigger = new SizeBasedTriggeringPolicy<>();
                trigger.setContext(ctx);
                trigger.setMaxFileSize(FileSize.valueOf("2MB"));
                trigger.start();

                PatternLayoutEncoder encoder = new PatternLayoutEncoder();
                encoder.setContext(ctx);
                encoder.setPattern("%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} - %msg%n%ex");
                encoder.start();

                fileAppender.setRollingPolicy(rolling);
                fileAppender.setTriggeringPolicy(trigger);
                fileAppender.setEncoder(encoder);
                fileAppender.start();

                Logger root = ctx.getLogger(Logger.ROOT_LOGGER_NAME);
                root.addAppender(fileAppender);
                if (root.getLevel() == null) {
                    root.setLevel(Level.INFO);
                }
                installed = true;
                org.slf4j.Logger log = LoggerFactory.getLogger(CrewcastLogging.class);
                log.info("Crewcast file logging: {}", CrewcastPaths.logFile());
            } catch (Exception e) {
                System.err.println("Crewcast: could not install file logging: " + e.getMessage());
            }
        }
    }

    /** Redact secrets and paths outside crewcast home (logs/bin allowed). */
    public static String sanitize(String message) {
        if (message == null) {
            return null;
        }
        String s = ACCESS_KEY.matcher(message).replaceAll("$1<redacted>");
        return s;
    }

    private static final class PathUtil {
        static void ensureDir(java.nio.file.Path dir) throws java.io.IOException {
            Files.createDirectories(dir);
        }
    }
}
