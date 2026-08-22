package com.hotelapp.auth;

import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * RFC 6238 TOTP with a 30-second step and one-step clock window, matching
 * typical authenticator apps and the Rust two_factor implementation.
 */
@Component
public class Rfc6238TotpVerifier implements TotpVerifier {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    @Override
    public boolean verify(String secretBase32, String code) {
        if (secretBase32 == null || secretBase32.isBlank()
                || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        byte[] key = base32Decode(secretBase32.replace(" ", "").toUpperCase());
        long now = System.currentTimeMillis() / 1000L / 30L;
        for (int drift = -1; drift <= 1; drift++) {
            String candidate = hotp(key, now + drift);
            if (constantTimeEquals(candidate, code)) {
                return true;
            }
        }
        return false;
    }

    private static String hotp(byte[] key, long counter) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array());
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigestisEqual(a.getBytes(), b.getBytes());
    }

    private static boolean MessageDigestisEqual(byte[] a, byte[] b) {
        return java.security.MessageDigest.isEqual(a, b);
    }

    private static byte[] base32Decode(String input) {
        int buffer = 0;
        int bits = 0;
        byte[] out = new byte[input.length() * 5 / 8];
        int index = 0;
        for (char c : input.toCharArray()) {
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                continue;
            }
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out[index++] = (byte) (buffer >> (bits - 8));
                bits -= 8;
            }
        }
        byte[] trimmed = new byte[index];
        System.arraycopy(out, 0, trimmed, 0, index);
        return trimmed;
    }
}
