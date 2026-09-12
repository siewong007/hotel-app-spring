package com.hotelapp.auth;

import com.hotelapp.core.config.AppProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Port of the TOTP helpers on upstream {@code AuthService}: the {@code enc1:}
 * AES-256-GCM at-rest sealing, the base32 secret + otpauth URL generator, and
 * the backup/recovery-code format.
 *
 * <p>{@code TOTP_ENCRYPTION_KEY} decodes as 32-byte std-base64, 64-char hex, or
 * a raw ASCII string of at least 32 bytes (SHA-256 hashed to 32). Without a
 * configured key, secrets persist in plaintext so existing deployments keep
 * booting; sealed rows written before the key existed pass through unchanged.
 */
@Component
public class TotpSecrets {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AppProperties properties;

    public TotpSecrets(AppProperties properties) {
        this.properties = properties;
    }

    // ---- key handling ------------------------------------------------------

    /** {@code totp_encryption_key_from_config}: decode the configured key. */
    public byte[] encryptionKey() {
        String raw = properties.getTotpEncryptionKey();
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        raw = raw.trim();
        try {
            byte[] bytes = Base64.getDecoder().decode(raw);
            if (bytes.length == 32) {
                return bytes;
            }
        } catch (IllegalArgumentException ignored) {
        }
        if (raw.length() == 64) {
            try {
                byte[] bytes = new byte[32];
                for (int i = 0; i < 32; i++) {
                    bytes[i] = (byte) Integer.parseInt(raw.substring(i * 2, i * 2 + 2), 16);
                }
                return bytes;
            } catch (NumberFormatException ignored) {
            }
        }
        if (raw.length() >= 32) {
            try {
                return MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            } catch (java.security.NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
        }
        return null;
    }

    // ---- enc1: sealing -----------------------------------------------------

    /**
     * {@code encrypt_stored_totp_secret}: seal with AES-256-GCM under the
     * configured key; identity function when no key is configured.
     */
    public String seal(String plaintext) {
        byte[] key = encryptionKey();
        if (key == null) {
            return plaintext;
        }
        try {
            byte[] nonce = new byte[12];
            RANDOM.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, nonce));
            byte[] ciphertextAndTag = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + ciphertextAndTag.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(ciphertextAndTag, 0, payload, nonce.length, ciphertextAndTag.length);
            return "enc1:" + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (Exception e) {
            throw new IllegalStateException("TOTP secret sealing failed: " + e.getMessage());
        }
    }

    /**
     * {@code decrypt_stored_totp_secret}: legacy plaintext rows pass through;
     * {@code enc1:} rows require the key. The Java GCM cipher expects
     * ciphertext+tag concatenated, matching the upstream ring layout.
     */
    public String open(String stored) {
        if (stored == null || !stored.startsWith("enc1:")) {
            return stored;
        }
        byte[] key = encryptionKey();
        if (key == null) {
            throw new IllegalStateException(
                    "TOTP secret is encrypted but TOTP_ENCRYPTION_KEY is not configured");
        }
        try {
            byte[] payload = Base64.getUrlDecoder().decode(stored.substring(5));
            if (payload.length < 12 + 16) {
                throw new IllegalStateException("corrupt encrypted TOTP secret: too short");
            }
            byte[] nonce = new byte[12];
            byte[] inOut = new byte[payload.length - 12];
            System.arraycopy(payload, 0, nonce, 0, 12);
            System.arraycopy(payload, 12, inOut, 0, inOut.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"),
                    new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(inOut), StandardCharsets.UTF_8);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("encrypted TOTP secret failed authentication");
        }
    }

    // ---- generation ---------------------------------------------------------

    /**
     * {@code generate_totp_secret}: 20 random bytes → base32, plus the otpauth
     * URL the authenticator renders as a QR.
     */
    public record GeneratedSecret(String secretBase32, String qrCodeUrl) {
    }

    public GeneratedSecret generateSecret(String username, String issuerName) {
        byte[] secretBytes = new byte[20];
        RANDOM.nextBytes(secretBytes);
        String secretBase32 = base32Encode(secretBytes);
        String qrCodeUrl = "otpauth://totp/"
                + urlEncode(issuerName + ":" + username)
                + "?secret=" + secretBase32
                + "&issuer=" + urlEncode(issuerName)
                + "&algorithm=SHA1&digits=6&period=30";
        return new GeneratedSecret(secretBase32, qrCodeUrl);
    }

    /** {@code generate_backup_codes}: 10 codes, 10 random bytes each, hex + dashes. */
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            byte[] bytes = new byte[10];
            RANDOM.nextBytes(bytes);
            StringBuilder hex = new StringBuilder(20);
            for (byte b : bytes) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16))
                        .append(Character.forDigit(b & 0xF, 16));
            }
            String upper = hex.toString().toUpperCase();
            codes.add(upper.substring(0, 5) + "-" + upper.substring(5, 10) + "-"
                    + upper.substring(10, 15) + "-" + upper.substring(15, 20));
        }
        return codes;
    }

    /** {@code hash_recovery_code}: SHA-256 of the normalised (trimmed, upper) code. */
    public static String hashRecoveryCode(String code) {
        return AuthService.sha256Hex(code.trim().toUpperCase());
    }

    /** Whether a stored value is already a recovery-code hash. */
    static boolean isRecoveryCodeHash(String code) {
        if (code == null || code.length() != 64) {
            return false;
        }
        for (char c : code.toCharArray()) {
            if (!((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F'))) {
                return false;
            }
        }
        return true;
    }

    /** {@code recovery_codes_for_storage}: hash-or-pass-through each entry. */
    public List<String> recoveryCodesForStorage(List<String> codes) {
        List<String> out = new ArrayList<>(codes.size());
        for (String code : codes) {
            String trimmed = code.trim();
            out.add(isRecoveryCodeHash(trimmed) ? trimmed.toLowerCase() : hashRecoveryCode(trimmed));
        }
        return out;
    }

    /**
     * {@code check_recovery_code}: constant-time match over stored codes
     * (hash or legacy plaintext), returning the index of the match.
     */
    public static Integer checkRecoveryCode(String providedCode, List<String> storedCodes) {
        if (providedCode == null || storedCodes == null) {
            return null;
        }
        String providedHash = hashRecoveryCode(providedCode);
        String providedNormalized = providedCode.trim().toUpperCase();
        for (int i = 0; i < storedCodes.size(); i++) {
            String storedTrimmed = storedCodes.get(i) == null ? "" : storedCodes.get(i).trim();
            boolean matches = isRecoveryCodeHash(storedTrimmed)
                    ? MessageDigest.isEqual(providedHash.getBytes(StandardCharsets.UTF_8),
                            storedTrimmed.toLowerCase().getBytes(StandardCharsets.UTF_8))
                    : MessageDigest.isEqual(providedNormalized.getBytes(StandardCharsets.UTF_8),
                            storedTrimmed.toUpperCase().getBytes(StandardCharsets.UTF_8));
            if (matches) {
                return i;
            }
        }
        return null;
    }

    private static String base32Encode(byte[] data) {
        StringBuilder out = new StringBuilder((data.length * 8 + 4) / 5);
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                out.append(BASE32_ALPHABET.charAt((buffer >> (bitsLeft - 5)) & 0x1F));
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            out.append(BASE32_ALPHABET.charAt((buffer << (5 - bitsLeft)) & 0x1F));
        }
        return out.toString();
    }

    private static String urlEncode(String value) {
        try {
            return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
