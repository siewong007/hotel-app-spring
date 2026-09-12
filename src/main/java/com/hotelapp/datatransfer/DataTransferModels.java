package com.hotelapp.datatransfer;

import com.hotelapp.core.error.ApiError;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wire and catalog shapes for the data-transfer subsystem, ported from
 * {@code models/data_transfer.rs} + {@code repositories/data_transfer.rs}.
 */
public final class DataTransferModels {

    private DataTransferModels() {
    }

    /** {@code QualifiedTable} — a `schema.name` transfer-table key. */
    public record QualifiedTable(String schema, String name) {

        public static QualifiedTable parse(String key) {
            int dot = key == null ? -1 : key.indexOf('.');
            if (dot < 0) {
                throw ApiError.badRequest(
                        "Transfer table '" + key + "' must be schema-qualified");
            }
            String schema = key.substring(0, dot);
            String name = key.substring(dot + 1);
            if (key.indexOf('.', dot + 1) >= 0 || !isIdentifier(schema) || !isIdentifier(name)) {
                throw ApiError.badRequest("Invalid transfer table '" + key + "'");
            }
            return new QualifiedTable(schema, name);
        }

        public String key() {
            return schema + "." + name;
        }

        public String quoted() {
            return quoteIdentifier(schema) + "." + quoteIdentifier(name);
        }
    }

    /** {@code TransferTable} — one catalog row with columns, pks, deps. */
    public record TransferTable(QualifiedTable table, boolean isPartitioned,
            Set<String> columns, Set<String> generatedColumns,
            List<String> primaryKeyColumns, Set<String> dependencies) {

        /** Partitioned parents route; ordinary tables read ONLY themselves. */
        public String source() {
            return isPartitioned ? table.quoted() : "ONLY " + table.quoted();
        }
    }

    /** {@code RelaxedForeignKey} — an FK made deferrable inside an import tx. */
    public record RelaxedForeignKey(QualifiedTable table, String constraint) {
    }

    /** {@code TransferTablePreview}. */
    public record TransferTablePreview(String name, long count,
            List<String> dependencies) {
    }

    /** {@code ExportPreview}. */
    public record ExportPreview(String generatedAt, Map<String, Long> counts,
            long totalRecords, List<TransferTablePreview> tables) {
    }

    /** {@code FullDataExport} — the v2 schema-driven payload. */
    public record FullDataExport(String version,
            @com.fasterxml.jackson.annotation.JsonProperty("exported_at") String exportedAt,
            Map<String, List<Map<String, Object>>> tables) {
    }

    /** {@code ImportRowPolicy} — per-table insert rules for the v1 path. */
    public record ImportRowPolicy(Set<String> skipColumns, Set<String> validColumns,
            Set<String> requiredColumns, Set<String> userFkColumns,
            long fallbackUserId) {
    }

    static boolean isIdentifier(String value) {
        if (value == null || value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (!(Character.isLetterOrDigit(c) && c < 128) && c != '_') {
                return false;
            }
        }
        return true;
    }

    public static String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
