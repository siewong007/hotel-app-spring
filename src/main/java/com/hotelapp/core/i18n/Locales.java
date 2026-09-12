package com.hotelapp.core.i18n;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Port of {@code core/i18n.rs}: locale negotiation plus the message catalog
 * used by server-rendered guest email. Catalogs ship verbatim under
 * {@code resources/locales/} and use {@code {{name}}} placeholders.
 */
@Component
public class Locales {

    public static final String DEFAULT_LOCALE = "en";
    public static final List<String> SUPPORTED_LOCALES = List.of("en", "ms");
    public static final String DEFAULT_LOCALE_SETTING_KEY = "default_locale";

    private static final Logger log = LoggerFactory.getLogger(Locales.class);
    private static volatile Map<String, Map<String, String>> catalogs;

    /** A resolved supported locale. */
    public record Locale(String tag) {

        /** Look up a catalog entry, falling back to English, then the key. */
        public String message(String key) {
            String value = catalog(tag).get(key);
            if (value == null) {
                value = catalog(DEFAULT_LOCALE).get(key);
            }
            return value != null ? value : key;
        }

        /** Look up an entry and substitute {@code {{name}}} placeholders. */
        public String format(String key, Map<String, String> vars) {
            return interpolate(message(key), vars);
        }

        /** Render a date as {@code 26 Jul 2026} with catalog month names. */
        public String formatDate(LocalDate date) {
            String month = message(String.format("email.months.%02d", date.getMonthValue()));
            return String.format("%02d %s %d", date.getDayOfMonth(), month, date.getYear());
        }
    }

    /** Resolve a tag to a supported locale by primary subtag, else null. */
    public static Locale parse(String tag) {
        if (tag == null) {
            return null;
        }
        String primary = tag.trim().split("[-_]")[0].toLowerCase();
        if (primary.isEmpty()) {
            return null;
        }
        return SUPPORTED_LOCALES.contains(primary) ? new Locale(primary) : null;
    }

    /** Walk a preference chain, most specific first, ending at the default. */
    public static Locale resolve(String... candidates) {
        for (String candidate : candidates) {
            Locale locale = parse(candidate);
            if (locale != null) {
                return locale;
            }
        }
        return new Locale(DEFAULT_LOCALE);
    }

    static Map<String, String> catalog(String locale) {
        Map<String, Map<String, String>> all = catalogs;
        if (all == null) {
            synchronized (Locales.class) {
                all = catalogs;
                if (all == null) {
                    all = loadAll();
                    catalogs = all;
                }
            }
        }
        return all.getOrDefault(locale, all.get(DEFAULT_LOCALE));
    }

    private static Map<String, Map<String, String>> loadAll() {
        Map<String, Map<String, String>> all = new HashMap<>();
        ObjectMapper mapper = new ObjectMapper();
        for (String locale : SUPPORTED_LOCALES) {
            Map<String, String> entries = new HashMap<>();
            try (InputStream in = Locales.class.getResourceAsStream(
                    "/locales/" + locale + ".json")) {
                if (in != null) {
                    flatten("", mapper.readTree(in), entries);
                }
            } catch (Exception e) {
                log.error("Failed to parse the '{}' message catalog: {}", locale, e.getMessage());
            }
            all.put(locale, entries);
        }
        return all;
    }

    private static void flatten(String prefix, JsonNode node, Map<String, String> out) {
        if (node == null) {
            return;
        }
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                String path = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flatten(path, entry.getValue(), out);
            });
        } else if (node.isTextual() && !prefix.isEmpty()) {
            out.put(prefix, node.asText());
        }
    }

    /** Substitute {@code {{name}}} placeholders, leaving unknown ones verbatim. */
    static String interpolate(String template, Map<String, String> vars) {
        if (!template.contains("{{")) {
            return template;
        }
        StringBuilder out = new StringBuilder(template.length());
        String rest = template;
        int start;
        while ((start = rest.indexOf("{{")) >= 0) {
            out.append(rest, 0, start);
            String after = rest.substring(start + 2);
            int end = after.indexOf("}}");
            if (end < 0) {
                out.append(rest, start, rest.length());
                return out.toString();
            }
            String name = after.substring(0, end).trim();
            String value = vars.get(name);
            if (value != null) {
                out.append(value);
            } else {
                out.append("{{").append(after, 0, end).append("}}");
            }
            rest = after.substring(end + 2);
        }
        out.append(rest);
        return out.toString();
    }
}
