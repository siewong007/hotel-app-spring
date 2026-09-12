package com.hotelapp.communications;

import com.hotelapp.core.error.ApiError;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.util.Properties;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Email transport abstraction, mirroring
 * {@code modules/communications/transport.rs}: the provider is configured
 * exclusively from {@code SMTP_*} environment variables — never from
 * system_settings or any client-visible surface.
 */
@Component
public class SmtpTransport {

    public record OutgoingEmail(String to, String subject, String bodyHtml, String bodyText) {
    }

    public record SmtpConfig(
            String host,
            int port,
            String username,
            String password,
            String fromEmail,
            String fromName,
            String security) {

        /** None when SMTP_HOST or SMTP_FROM_EMAIL is unset/blank. */
        public static SmtpConfig fromEnv() {
            return fromValues(System::getenv);
        }

        static SmtpConfig fromValues(java.util.function.Function<String, String> get) {
            java.util.function.Function<String, String> env =
                    key -> {
                        String value = get.apply(key);
                        return value == null || value.trim().isEmpty() ? null : value;
                    };
            String host = env.apply("SMTP_HOST");
            String fromEmail = env.apply("SMTP_FROM_EMAIL");
            if (host == null || fromEmail == null) {
                return null;
            }
            int port = 587;
            String rawPort = env.apply("SMTP_PORT");
            if (rawPort != null) {
                try {
                    port = Integer.parseInt(rawPort);
                } catch (NumberFormatException ignored) {
                    port = 587;
                }
            }
            String security = env.apply("SMTP_SECURITY");
            return new SmtpConfig(
                    host, port, env.apply("SMTP_USERNAME"), env.apply("SMTP_PASSWORD"),
                    fromEmail, env.apply("SMTP_FROM_NAME"),
                    security == null ? "starttls" : security);
        }
    }

    /** {@code email_transport_configured} — scheduling readiness check. */
    public boolean isConfigured() {
        return SmtpConfig.fromEnv() != null;
    }

    /**
     * {@code Transport::send}: sends one message. Errors surface as strings so
     * callers can persist them as {@code last_error} without leaking typed
     * internals.
     */
    public String send(OutgoingEmail email) throws Exception {
        SmtpConfig config = SmtpConfig.fromEnv();
        if (config == null) {
            throw ApiError.conflict(
                    "Email transport is not configured; set SMTP_* environment variables");
        }
        JavaMailSender sender = buildSender(config);
        MimeMessage message = sender.createMimeMessage();
        boolean multipart = email.bodyText() != null;
        MimeMessageHelper helper = new MimeMessageHelper(message, multipart, "UTF-8");
        String from = config.fromName() != null
                ? config.fromName() + " <" + config.fromEmail() + ">"
                : config.fromEmail();
        helper.setFrom(new InternetAddress(from));
        helper.setTo(email.to());
        helper.setSubject(email.subject());
        if (multipart) {
            helper.setText(email.bodyText(), email.bodyHtml());
        } else {
            helper.setText(email.bodyHtml(), true);
        }
        sender.send(message);
        return null;
    }

    private JavaMailSender buildSender(SmtpConfig config) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(config.host());
        sender.setPort(config.port());
        if (config.username() != null && config.password() != null) {
            sender.setUsername(config.username());
            sender.setPassword(config.password());
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        switch (config.security()) {
            case "tls" -> {
                props.put("mail.smtp.ssl.enable", "true");
                props.put("mail.smtp.starttls.enable", "false");
            }
            case "none" -> props.put("mail.smtp.starttls.enable", "false");
            default -> {
                props.put("mail.smtp.starttls.enable", "true");
                props.put("mail.smtp.starttls.required", "true");
            }
        }
        if (config.username() != null) {
            props.put("mail.smtp.auth", "true");
        }
        return sender;
    }
}
