package com.labmind.business.chat.auth.support;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class AuthPasswordHasher {

    private static final int SALT_BYTES = 16;

    private final SecureRandom secureRandom = new SecureRandom();

    public String newSalt() {
        byte[] salt = new byte[SALT_BYTES];
        secureRandom.nextBytes(salt);
        return HexFormat.of().formatHex(salt);
    }

    public String hash(String rawPassword, String salt) {
        if (!StringUtils.hasText(rawPassword)) {
            throw new IllegalArgumentException("password must not be blank");
        }
        if (!StringUtils.hasText(salt)) {
            throw new IllegalArgumentException("password salt must not be blank");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt.getBytes(StandardCharsets.UTF_8));
            digest.update(rawPassword.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception error) {
            throw new IllegalStateException("failed to hash password", error);
        }
    }

    public boolean matches(String rawPassword, String salt, String storedHash) {
        if (!StringUtils.hasText(storedHash)) {
            return false;
        }
        return MessageDigest.isEqual(
                hash(rawPassword, salt).getBytes(StandardCharsets.UTF_8),
                storedHash.getBytes(StandardCharsets.UTF_8));
    }
}
