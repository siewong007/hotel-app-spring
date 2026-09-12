package com.hotelapp.email;

import com.hotelapp.core.config.AppProperties;

/**
 * Port of {@code modules/communications/email_layout.rs}: shared chrome for
 * guest-facing transactional email — table-based with inline styles because
 * Gmail and Outlook strip {@code <style>} blocks.
 */
public final class EmailLayout {

    private EmailLayout() {
    }

    public record Cta(String label, String url) {
    }

    public record RenderedEmail(String html, String text) {
    }

    public static String hotelDisplayName(AppProperties props) {
        String value = props.getSmtpFromName();
        if (value == null || value.trim().isEmpty()) {
            return "Salim Inn";
        }
        return value.trim();
    }

    public static String publicBaseUrl(AppProperties props) {
        String value = props.getPublicBaseUrl();
        if (value == null || value.trim().isEmpty()) {
            return "http://localhost:3000";
        }
        return value.trim().replaceAll("/+$", "");
    }

    public static String absoluteUrl(AppProperties props, String path) {
        String normalized = path.startsWith("/") ? path : "/" + path;
        return publicBaseUrl(props) + normalized;
    }

    /** Host portion of the configured public base URL, e.g. saliminn.my. */
    public static String canonicalHost(AppProperties props) {
        return hostOf(publicBaseUrl(props));
    }

    static String hostOf(String base) {
        String withoutScheme = base.contains("://")
                ? base.substring(base.indexOf("://") + 3)
                : base;
        int end = withoutScheme.length();
        for (char c : new char[] {'/', '?', '#'}) {
            int idx = withoutScheme.indexOf(c);
            if (idx >= 0) {
                end = Math.min(end, idx);
            }
        }
        return withoutScheme.substring(0, end);
    }

    /** Mirrors validation::html_escape. */
    public static String htmlEscape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (char c : value.toCharArray()) {
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    /**
     * A visual identity block naming the hotel and its canonical host — an
     * identity cue, not proof; the seal itself says so.
     */
    public static String identitySealHtml(AppProperties props) {
        String hotel = htmlEscape(hotelDisplayName(props));
        String host = htmlEscape(canonicalHost(props));
        return "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"margin:26px 0 4px;border-collapse:collapse;\">"
                + "<tr><td style=\"padding:14px 16px;background:#fffdf7;border:1px solid #d9b572;"
                + "border-radius:10px;font-family:Arial,Helvetica,sans-serif;\">"
                + "<div style=\"font-family:Georgia,'Times New Roman',serif;font-size:15px;"
                + "letter-spacing:0.06em;color:#102a21;\">" + hotel + "</div>"
                + "<div style=\"font-size:12px;color:#5b7268;padding-top:2px;\">" + host + "</div>"
                + "<div style=\"font-size:11px;color:#5b7268;line-height:1.5;padding-top:8px;\">"
                + "This seal is a visual cue only and can be copied. What actually proves "
                + "this mail is ours is your provider's sender-domain check, and reaching "
                + host + " by typing it yourself rather than following a link."
                + "</div></td></tr></table>";
    }

    public static String identitySealText(AppProperties props) {
        String hotel = hotelDisplayName(props);
        String host = canonicalHost(props);
        return "-- " + hotel + " (" + host + ") --\n"
                + "This seal is a visual cue only and can be copied. What actually proves this mail "
                + "is ours is your provider's sender-domain check, and reaching " + host
                + " by typing it yourself rather than following a link.\n";
    }

    /** Label/value rows as an email-safe table. Values are escaped. */
    public static String detailsTable(String[][] rows) {
        if (rows == null || rows.length == 0) {
            return "";
        }
        StringBuilder html = new StringBuilder(
                "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
                        + "style=\"margin:20px 0;border-collapse:collapse;\">");
        for (int i = 0; i < rows.length; i++) {
            String border = i + 1 == rows.length ? "none" : "1px solid #e6eee9";
            html.append("<tr>")
                    .append("<td style=\"padding:10px 0;border-bottom:").append(border)
                    .append(";width:38%;font-size:13px;color:#5b7268;font-family:Arial,Helvetica,sans-serif;\">")
                    .append(htmlEscape(rows[i][0])).append("</td>")
                    .append("<td style=\"padding:10px 0;border-bottom:").append(border)
                    .append(";font-size:14px;color:#102a21;font-weight:700;"
                            + "font-family:Arial,Helvetica,sans-serif;\">")
                    .append(htmlEscape(rows[i][1])).append("</td></tr>");
        }
        return html.append("</table>").toString();
    }

    public static RenderedEmail render(AppProperties props, String preheader, String heading,
            String innerHtml, String innerText, Cta cta) {
        String hotel = hotelDisplayName(props);
        String hotelHtml = htmlEscape(hotel);
        String headingHtml = htmlEscape(heading);
        String preheaderHtml = htmlEscape(preheader);
        String site = publicBaseUrl(props);
        String ctaHtml = cta == null
                ? ""
                : "<table role=\"presentation\" cellspacing=\"0\" cellpadding=\"0\" "
                        + "style=\"margin:28px 0 8px;\"><tr><td bgcolor=\"#0E8C6A\" "
                        + "style=\"border-radius:8px;\"><a href=\"" + htmlEscape(cta.url())
                        + "\" style=\"display:inline-block;padding:12px 22px;color:#ffffff;"
                        + "text-decoration:none;font-weight:700;font-size:14px;"
                        + "font-family:Arial,Helvetica,sans-serif;\">" + htmlEscape(cta.label())
                        + "</a></td></tr></table>";
        String html = "<!DOCTYPE html><html lang=\"en\">"
                + "<head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width\"></head>"
                + "<body style=\"margin:0;padding:0;background:#f4f7f4;\">"
                + "<div style=\"display:none;max-height:0;overflow:hidden;mso-hide:all;\">"
                + preheaderHtml + "</div>"
                + "<table role=\"presentation\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"background:#f4f7f4;\"><tr><td align=\"center\" style=\"padding:24px 12px;\">"
                + "<table role=\"presentation\" width=\"600\" cellspacing=\"0\" cellpadding=\"0\" "
                + "style=\"max-width:600px;width:100%;background:#ffffff;border-radius:12px;"
                + "overflow:hidden;border:1px solid #e6eee9;\">"
                + "<tr><td style=\"background:#102a21;padding:22px 28px;\">"
                + "<div style=\"font-family:Georgia,'Times New Roman',serif;font-size:22px;"
                + "letter-spacing:0.08em;color:#fffdf7;\">" + hotelHtml + "</div></td></tr>"
                + "<tr><td style=\"height:4px;background:#d9b572;font-size:0;line-height:0;\">"
                + "&nbsp;</td></tr>"
                + "<tr><td style=\"padding:28px;font-family:Arial,Helvetica,sans-serif;"
                + "color:#102a21;font-size:15px;line-height:1.55;\">"
                + "<h1 style=\"margin:0 0 16px;font-size:22px;font-weight:700;color:#102a21;\">"
                + headingHtml + "</h1>" + innerHtml + ctaHtml + "</td></tr>"
                + "<tr><td style=\"padding:16px 28px 24px;background:#f4f7f4;"
                + "font-family:Arial,Helvetica,sans-serif;font-size:12px;line-height:1.5;"
                + "color:#5b7268;\">This message was sent by " + hotelHtml + "."
                + "<br><a href=\"" + site + "\" style=\"color:#0E8C6A;text-decoration:none;\">"
                + site + "</a></td></tr></table></td></tr></table></body></html>";

        StringBuilder text = new StringBuilder(hotel + "\n" + heading + "\n\n"
                + innerText.trim() + "\n");
        if (cta != null) {
            text.append("\n").append(cta.label()).append(": ").append(cta.url()).append("\n");
        }
        text.append("\nThis message was sent by ").append(hotel).append(".\n")
                .append(site).append("\n");
        return new RenderedEmail(html, text.toString());
    }
}
