package com.solocrew.crewcast.access;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.HexFormat;

@Component
public class AccessSession {

    public static final String COOKIE_NAME = "crewcast_access";
    public static final String QUERY_PARAM = "t";

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();

    private volatile String token = generateToken();

    public String getToken() {
        return token;
    }

    public String regenerateToken() {
        token = generateToken();
        return token;
    }

    public boolean matches(String candidate) {
        return candidate != null && !candidate.isBlank() && candidate.equals(token);
    }

    private static String generateToken() {
        byte[] bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        return HEX.formatHex(bytes);
    }
}
