package com.smartAiUniversityAssistant.seniorproject.security;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenSupport {
    private static final SecureRandom RANDOM = new SecureRandom();
    private TokenSupport() {}
    public static String random(int bytes) {
        byte[] value = new byte[bytes]; RANDOM.nextBytes(value);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value);
    }
    public static String digest(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException("SHA-256 unavailable"); }
    }
    public static boolean canonical(String value, int bytes) {
        if (value == null || value.length() != (bytes * 8 + 5) / 6 || !value.matches("[A-Za-z0-9_-]+")) return false;
        try { return Base64.getUrlEncoder().withoutPadding().encodeToString(Base64.getUrlDecoder().decode(value)).equals(value); }
        catch (IllegalArgumentException e) { return false; }
    }
    public static boolean userId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) return false;
        try { return Long.parseLong(value) > 0; } catch (NumberFormatException e) { return false; }
    }
}
