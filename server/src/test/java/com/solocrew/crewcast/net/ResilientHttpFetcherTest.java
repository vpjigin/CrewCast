package com.solocrew.crewcast.net;

import com.solocrew.crewcast.support.CrewcastSpringTestBase;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResilientHttpFetcherTest extends CrewcastSpringTestBase {

    private HttpServer server;
    private int port;
    private final AtomicInteger attempts = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        attempts.set(0);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        port = server.getAddress().getPort();
        server.start();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void retriesOn503ThenSucceeds() throws Exception {
        server.createContext("/file", exchange -> {
            int n = attempts.incrementAndGet();
            if (n < 3) {
                respond(exchange, 503, "busy");
            } else {
                respond(exchange, 200, "ok-data");
            }
        });
        Path dest = isolatedHome().resolve("bin/test.bin");
        Files.createDirectories(dest.getParent());
        ResilientHttpFetcher fetcher = new ResilientHttpFetcher("test");
        long bytes = fetcher.downloadToFile("http://127.0.0.1:" + port + "/file", dest, 7, pct -> {});
        assertEquals(7, bytes);
        assertEquals("ok-data", Files.readString(dest));
        assertTrue(attempts.get() >= 3);
    }

    @Test
    void checksumMismatchMessage() {
        String plain = DownloadErrors.plainReason(new IOException("Checksum mismatch (expected abc, got def)"), "http://x");
        assertEquals("Checksum mismatch", plain);
    }

    @Test
    void failsAfterMaxRetries() throws Exception {
        server.createContext("/always", exchange -> respond(exchange, 503, "nope"));
        Path dest = isolatedHome().resolve("bin/fail.bin");
        ResilientHttpFetcher fetcher = new ResilientHttpFetcher("test");
        assertThrows(IOException.class, () -> fetcher.downloadToFile("http://127.0.0.1:" + port + "/always", dest, 0, p -> {}));
        assertTrue(!Files.exists(dest) || Files.size(dest) == 0);
    }

    private static void respond(HttpExchange exchange, int code, String body) throws IOException {
        byte[] data = body.getBytes();
        exchange.sendResponseHeaders(code, data.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(data);
        }
    }
}
