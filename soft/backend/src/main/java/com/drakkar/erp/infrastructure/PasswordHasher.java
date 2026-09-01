package com.drakkar.erp.infrastructure;

import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

@Component
public class PasswordHasher {
    static final int ITERATIONS = 210_000;
    static final int KEY_LENGTH_BITS = 256;
    private final SecureRandom random = new SecureRandom();

    public record EncodedPassword(byte[] salt, byte[] hash) {
    }

    public EncodedPassword encode(char[] password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        return new EncodedPassword(salt, hash(password, salt));
    }

    public boolean matches(char[] password, byte[] salt, byte[] expectedHash) {
        byte[] actualHash = hash(password, salt);
        return MessageDigest.isEqual(actualHash, expectedHash);
    }

    private byte[] hash(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Криптографический алгоритм PBKDF2 недоступен", exception);
        } finally {
            spec.clearPassword();
            java.util.Arrays.fill(password, '\0');
        }
    }
}
