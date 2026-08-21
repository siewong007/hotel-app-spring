package com.hotelapp.core.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;

class DataSourceUrlParserTest {

    @Test
    void parsesFullDatabaseUrl() {
        DataSourceProperties properties = new DataSourceProperties();

        DataSourceUrlParser.applyDatabaseUrl(
                properties, "postgres://user:secret@db.example.com:6543/hoteldb");

        assertThat(properties.getUrl())
                .isEqualTo("jdbc:postgresql://db.example.com:6543/hoteldb");
        assertThat(properties.getUsername()).isEqualTo("user");
        assertThat(properties.getPassword()).isEqualTo("secret");
    }

    @Test
    void defaultsPortTo5432AndAcceptsPostgresqlScheme() {
        DataSourceProperties properties = new DataSourceProperties();

        DataSourceUrlParser.applyDatabaseUrl(properties, "postgresql://app@localhost/appdb");

        assertThat(properties.getUrl()).isEqualTo("jdbc:postgresql://localhost:5432/appdb");
        assertThat(properties.getUsername()).isEqualTo("app");
    }

    @Test
    void decodesPercentEncodedCredentials() {
        DataSourceProperties properties = new DataSourceProperties();

        DataSourceUrlParser.applyDatabaseUrl(
                properties, "postgres://user:p%40ss%3Aw0rd@localhost:5432/db");

        assertThat(properties.getUsername()).isEqualTo("user");
        assertThat(properties.getPassword()).isEqualTo("p@ss:w0rd");
    }

    @Test
    void blankDatabaseUrlKeepsDiscretePropertiesForFallback() {
        DataSourceProperties properties = new DataSourceProperties();
        properties.setUrl("jdbc:postgresql://fallback:5432/hotelapp");
        properties.setUsername("hotelapp");
        properties.setPassword("hotelapp");

        String databaseUrl = null;
        if (databaseUrl != null && !databaseUrl.isBlank()) {
            DataSourceUrlParser.applyDatabaseUrl(properties, databaseUrl);
        }

        assertThat(properties.getUrl())
                .isEqualTo("jdbc:postgresql://fallback:5432/hotelapp");
        assertThat(properties.getUsername()).isEqualTo("hotelapp");
        assertThat(properties.getPassword()).isEqualTo("hotelapp");
    }

    @Test
    void rejectsUnsupportedScheme() {
        DataSourceProperties properties = new DataSourceProperties();

        assertThatThrownBy(() -> DataSourceUrlParser.applyDatabaseUrl(
                        properties, "mysql://user:pass@localhost:3306/db"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported DATABASE_URL scheme 'mysql'");
    }

    @Test
    void rejectsMissingDatabaseName() {
        DataSourceProperties properties = new DataSourceProperties();

        assertThatThrownBy(() -> DataSourceUrlParser.applyDatabaseUrl(
                        properties, "postgres://user:pass@localhost:5432"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing database name");
    }
}
