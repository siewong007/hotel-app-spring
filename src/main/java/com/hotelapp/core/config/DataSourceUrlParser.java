package com.hotelapp.core.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Configures the datasource from the DATABASE_URL environment variable
 * (format postgres://user:pass@host:port/dbname). Falls back to the discrete
 * spring.datasource.* properties (e.g. SPRING_DATASOURCE_URL) only when
 * DATABASE_URL is unset.
 */
@Configuration
public class DataSourceUrlParser {

    private static final Logger log = LoggerFactory.getLogger(DataSourceUrlParser.class);

    @Bean
    public DataSource dataSource(Environment environment) {
        DataSourceProperties properties = new DataSourceProperties();
        Binder.get(environment)
                .bind("spring.datasource", Bindable.ofInstance(properties));
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            log.info("Configuring datasource from DATABASE_URL");
            applyDatabaseUrl(properties, databaseUrl.trim());
        } else {
            log.info("DATABASE_URL not set; using spring.datasource.* properties"
                    + " (SPRING_DATASOURCE_* fallback)");
        }
        return DataSourceBuilder.create()
                .url(properties.getUrl())
                .username(properties.getUsername())
                .password(properties.getPassword())
                .build();
    }

    static void applyDatabaseUrl(DataSourceProperties properties, String databaseUrl) {
        URI uri;
        try {
            uri = new URI(databaseUrl);
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Invalid DATABASE_URL, expected postgres://user:pass@host:port/dbname: "
                            + e.getMessage(),
                    e);
        }
        if (!"postgres".equals(uri.getScheme()) && !"postgresql".equals(uri.getScheme())) {
            throw new IllegalStateException(
                    "Unsupported DATABASE_URL scheme '" + uri.getScheme()
                            + "', expected postgres:// or postgresql://");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Invalid DATABASE_URL: missing host");
        }
        String path = uri.getPath();
        String database =
                (path != null && !path.isEmpty()) ? decode(path.substring(1)) : "";
        if (database.isBlank()) {
            throw new IllegalStateException("Invalid DATABASE_URL: missing database name");
        }
        int port = uri.getPort() != -1 ? uri.getPort() : 5432;

        String username = null;
        String password = null;
        String userInfo = uri.getUserInfo();
        if (userInfo != null) {
            int separator = userInfo.indexOf(':');
            if (separator >= 0) {
                username = decode(userInfo.substring(0, separator));
                password = decode(userInfo.substring(separator + 1));
            } else {
                username = decode(userInfo);
            }
        }

        properties.setUrl("jdbc:postgresql://" + host + ":" + port + "/" + database);
        properties.setUsername(username);
        properties.setPassword(password);
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "Invalid percent-encoding in DATABASE_URL component: " + e.getMessage(), e);
        }
    }
}
