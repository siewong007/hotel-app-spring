package com.hotelapp.email;

import com.hotelapp.auth.AuthService;
import com.hotelapp.core.config.AppProperties;
import com.hotelapp.core.i18n.Locales;
import com.hotelapp.core.settings.HotelSettings;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Port of {@code services/account_emails.rs}: mint an email-verification
 * token and queue the mail carrying it — a single operation so no caller can
 * mint a token without the mail.
 *
 * <p>Deliveries are filed as {@code booking_confirmation} kind/topic (the
 * closed CHECK lists make it the sanctioned transactional pair); the guest
 * cannot opt out of it, while hard suppressions still apply.
 */
@Component
public class AccountEmails {

    private static final Logger log = LoggerFactory.getLogger(AccountEmails.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AppProperties props;
    private final HotelSettings settings;

    public AccountEmails(JdbcTemplate jdbc, AppProperties props, HotelSettings settings) {
        this.jdbc = jdbc;
        this.props = props;
        this.settings = settings;
    }

    private record VerificationRecipient(
            Long guestId, String email, String fullName, String guestLocale) {
    }

    /**
     * {@code send_email_verification}: best-effort by contract — no-op when
     * the account has no deliverable address or no guest profile to file the
     * delivery against.
     */
    @Transactional
    public void sendEmailVerification(long userId) {
        List<VerificationRecipient> rows = jdbc.query(
                "SELECT u.guest_id, u.email, u.full_name, g.language_preference AS guest_locale "
                        + "FROM users u LEFT JOIN guests g ON g.id = u.guest_id "
                        + "WHERE u.id = ? AND u.deleted_at IS NULL",
                (rs, i) -> new VerificationRecipient(
                        (Long) rs.getObject("guest_id"),
                        rs.getString("email"),
                        rs.getString("full_name"),
                        rs.getString("guest_locale")),
                userId);
        if (rows.isEmpty()) {
            return;
        }
        VerificationRecipient recipient = rows.get(0);

        // Reserved, non-deliverable placeholder addresses need no mail.
        String email = recipient.email() == null ? null : recipient.email().trim();
        if (email == null || email.isEmpty() || email.endsWith("@no-email.invalid")) {
            return;
        }
        if (recipient.guestId() == null) {
            log.warn("Skipping verification email for user {}: no guest profile to file it against",
                    userId);
            return;
        }

        String hotelDefault = settings.getString(
                Locales.DEFAULT_LOCALE_SETTING_KEY, Locales.DEFAULT_LOCALE);
        Locales.Locale locale = Locales.resolve(recipient.guestLocale(), hotelDefault);

        // Minted last, so a token only ever exists alongside a queued mail.
        String token = createEmailVerificationToken(userId);
        String verifyUrl = EmailLayout.absoluteUrl(props, "/verify-email?token=" + token);

        String hotel = EmailLayout.hotelDisplayName(props);
        String fullName = recipient.fullName() == null ? "" : recipient.fullName();
        String subject = locale.format("email.verifyEmail.subject", Map.of("hotel", hotel));
        String greetingHtml = locale.format("email.greeting",
                Map.of("name", EmailLayout.htmlEscape(fullName)));
        String innerHtml = "<p>" + greetingHtml + "</p><p>"
                + locale.message("email.verifyEmail.bodyHtml") + "</p>";
        String innerText = locale.format("email.greeting", Map.of("name", fullName))
                + "\n" + locale.message("email.verifyEmail.bodyText");
        EmailLayout.RenderedEmail rendered = EmailLayout.render(
                props,
                locale.format("email.verifyEmail.preheader", Map.of("hotel", hotel)),
                locale.message("email.verifyEmail.heading"),
                innerHtml, innerText,
                new EmailLayout.Cta(locale.message("email.cta.verifyEmail"), verifyUrl));

        jdbc.update("""
                INSERT INTO email_deliveries
                    (campaign_id, kind, guest_id, topic, recipient_email, subject,
                     body_html, body_text, voucher_id, idempotency_key)
                VALUES (NULL, ?, ?, ?, LOWER(?), ?, ?, ?, NULL, ?)
                ON CONFLICT (idempotency_key) DO NOTHING
                """,
                "booking_confirmation", recipient.guestId(), "booking_confirmation",
                email, subject, rendered.html(), rendered.text(),
                "email-verification:" + userId + ":" + System.currentTimeMillis());
    }

    /** {@code try_send_email_verification}: never fails the caller. */
    public void trySendEmailVerification(long userId) {
        try {
            sendEmailVerification(userId);
        } catch (Exception e) {
            log.error("Failed to queue verification email for user {}: {}", userId,
                    e.getMessage(), e);
        }
    }

    /**
     * Port of {@code AuthService::create_email_verification_token}: mint a
     * 256-bit hex token, store only its SHA-256 digest with a 24-hour expiry.
     */
    public String createEmailVerificationToken(long userId) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        StringBuilder token = new StringBuilder(64);
        for (byte b : bytes) {
            token.append(Character.forDigit((b >> 4) & 0xF, 16))
                    .append(Character.forDigit(b & 0xF, 16));
        }
        jdbc.update("""
                UPDATE users
                SET email_verification_token = ?,
                    email_token_expires_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                AuthService.sha256Hex(token.toString()),
                Timestamp.from(Instant.now().plus(24, ChronoUnit.HOURS)),
                userId);
        return token.toString();
    }
}
