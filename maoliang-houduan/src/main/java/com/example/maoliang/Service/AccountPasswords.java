package com.example.maoliang.Service;

import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class AccountPasswords {
    private static final String PREFIX = "{pbkdf2}";
    private static final Pbkdf2PasswordEncoder ENCODER = Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private AccountPasswords() {}

    public static String encode(String password) { return PREFIX + ENCODER.encode(password); }

    public static boolean matches(String password, String stored) {
        if (password == null || stored == null) return false;
        if (stored.startsWith(PREFIX)) return ENCODER.matches(password, stored.substring(PREFIX.length()));
        return MessageDigest.isEqual(password.getBytes(StandardCharsets.UTF_8), stored.stripTrailing().getBytes(StandardCharsets.UTF_8));
    }
}
