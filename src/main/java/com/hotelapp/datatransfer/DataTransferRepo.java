package com.hotelapp.datatransfer;

import tools.jackson.databind.ObjectMapper;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.datatransfer.DataTransferModels.ImportRowPolicy;
import com.hotelapp.datatransfer.DataTransferModels.QualifiedTable;
import com.hotelapp.datatransfer.DataTransferModels.RelaxedForeignKey;
import com.hotelapp.datatransfer.DataTransferModels.TransferTable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Port of {@code repositories/data_transfer.rs} — the pg-catalog introspection
 * and row-level import/export SQL. Table names are only ever interpolated from
 * the fixed transferable whitelist; row data travels as bound jsonb.
 */
@Component
public class DataTransferRepo {

    /**
     * Whitelist for dynamically-built SQL table names. Mirrors
     * {@code services::data_transfer::TABLE_INSERT_ORDER} — the single source
     * of truth for which tables are transferable.
     */
    public static final List<String> TABLE_INSERT_ORDER = List.of(
            "amenities",
            "booking_channels",
            "companies",
            "corporate_accounts",
            "corporate_account_contacts",
            "email_templates",
            "guests",
            "promotions",
            "vouchers",
            "guest_documents",
            "guest_notes",
            "guest_preferences",
            "loyalty_programs",
            "loyalty_program_rules",
            "loyalty_tiers",
            "loyalty_memberships",
            "loyalty_members",
            "loyalty_accounts",
            "loyalty_rewards",
            "night_audit_runs",
            "night_audit_details",
            "points_transactions",
            "rate_plans",
            "reward_catalog",
            "room_status_transitions",
            "room_types",
            "promotion_room_types",
            "guest_complimentary_credits",
            "room_rates",
            "room_type_amenities",
            "rooms",
            "bookings",
            "voucher_redemptions",
            "voucher_redemption_allocations",
            "booking_guests",
            "booking_history",
            "booking_modifications",
            "customer_ledgers",
            "customer_ledger_payments",
            "guest_reviews",
            "housekeeping_tasks",
            "invoices",
            "maintenance_tickets",
            "night_audit_posted_nights",
            "payments",
            "loyalty_transactions",
            "reward_redemptions",
            "loyalty_redemptions",
            "room_changes",
            "room_history",
            "room_status_change_log",
            "self_checkin_events",
            "services",
            "booking_services",
            "system_settings",
            "user_guests");

    static final Set<String> KNOWN_TABLES = new HashSet<>(TABLE_INSERT_ORDER);

    /** {@code ensure_known_table}. */
    public static void ensureKnownTable(String table) {
        if (!KNOWN_TABLES.contains(table)) {
            throw ApiError.badRequest("Unknown table '" + table
                    + "' is not permitted for data-transfer operations");
        }
    }

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DataTransferRepo(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // Catalog introspection
    // ------------------------------------------------------------------

    /** {@code transfer_tables}: every base/partitioned table + its metadata. */
    public List<TransferTable> transferTables() {
        List<Map<String, Object>> tableRows = jdbc.queryForList("""
                SELECT namespace.nspname, class.relname, class.relkind::text
                FROM pg_class class
                JOIN pg_namespace namespace ON namespace.oid = class.relnamespace
                WHERE class.relkind IN ('r', 'p')
                  AND namespace.nspname <> 'information_schema'
                  AND namespace.nspname !~ '^pg_'
                  AND (class.relkind = 'p' OR NOT EXISTS (
                      SELECT 1 FROM pg_inherits WHERE inhrelid = class.oid
                  ))
                ORDER BY namespace.nspname, class.relname
                """);
        List<QualifiedTable> tables = new ArrayList<>(tableRows.size());
        Map<String, Boolean> partitioned = new HashMap<>();
        for (Map<String, Object> row : tableRows) {
            QualifiedTable table = new QualifiedTable((String) row.get("nspname"),
                    (String) row.get("relname"));
            tables.add(table);
            partitioned.put(table.key(), "p".equals(row.get("relkind")));
        }
        Set<String> known = new HashSet<>();
        for (QualifiedTable table : tables) {
            known.add(table.key());
        }
        Map<String, List<Set<String>>> columns = transferColumns(tables);
        Map<String, List<String>> primaryKeys = transferPrimaryKeys(tables);
        Map<String, Set<String>> dependencies = transferDependencies(known);

        List<TransferTable> result = new ArrayList<>(tables.size());
        for (QualifiedTable table : tables) {
            String key = table.key();
            List<Set<String>> columnPair =
                    columns.getOrDefault(key, List.of(Set.of(), Set.of()));
            result.add(new TransferTable(table, partitioned.get(key),
                    columnPair.get(0), columnPair.get(1),
                    primaryKeys.getOrDefault(key, List.of()),
                    dependencies.getOrDefault(key, Set.of())));
        }
        return result;
    }

    private Map<String, List<Set<String>>> transferColumns(List<QualifiedTable> tables) {
        Map<String, List<Set<String>>> metadata = new HashMap<>();
        for (QualifiedTable table : tables) {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT column_name, is_generated FROM information_schema.columns"
                            + " WHERE table_schema = ? AND table_name = ?",
                    table.schema(), table.name());
            Set<String> all = new HashSet<>();
            Set<String> generated = new HashSet<>();
            for (Map<String, Object> row : rows) {
                String column = (String) row.get("column_name");
                all.add(column);
                if (!"NEVER".equals(row.get("is_generated"))) {
                    generated.add(column);
                }
            }
            metadata.put(table.key(), List.of(all, generated));
        }
        return metadata;
    }

    /**
     * {@code transfer_dependencies}: child key -> parent keys. `constraint` is
     * a reserved word, so the catalog is aliased `foreign_key` (c7343b29).
     */
    private Map<String, Set<String>> transferDependencies(Set<String> known) {
        List<String[]> rows = jdbc.query("""
                SELECT child_namespace.nspname, child.relname,
                       parent_namespace.nspname, parent.relname
                FROM pg_constraint foreign_key
                JOIN pg_class child ON child.oid = foreign_key.conrelid
                JOIN pg_namespace child_namespace
                  ON child_namespace.oid = child.relnamespace
                JOIN pg_class parent ON parent.oid = foreign_key.confrelid
                JOIN pg_namespace parent_namespace
                  ON parent_namespace.oid = parent.relnamespace
                WHERE foreign_key.contype = 'f'
                """, (rs, i) -> new String[]{rs.getString(1), rs.getString(2),
                        rs.getString(3), rs.getString(4)});
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (String[] row : rows) {
            String childKey = row[0] + "." + row[1];
            String parentKey = row[2] + "." + row[3];
            if (!childKey.equals(parentKey) && known.contains(childKey)
                    && known.contains(parentKey)) {
                dependencies.computeIfAbsent(childKey, k -> new HashSet<>()).add(parentKey);
            }
        }
        return dependencies;
    }

    private Map<String, List<String>> transferPrimaryKeys(List<QualifiedTable> tables) {
        Map<String, List<String>> primaryKeys = new HashMap<>();
        for (QualifiedTable table : tables) {
            List<String> columns = jdbc.queryForList("""
                    SELECT attribute.attname
                    FROM pg_index idx
                    JOIN pg_class class ON class.oid = idx.indrelid
                    JOIN pg_namespace namespace ON namespace.oid = class.relnamespace
                    JOIN unnest(idx.indkey) WITH ORDINALITY key(attnum, position) ON true
                    JOIN pg_attribute attribute
                      ON attribute.attrelid = class.oid AND attribute.attnum = key.attnum
                    WHERE idx.indisprimary AND namespace.nspname = ? AND class.relname = ?
                    ORDER BY key.position
                    """, String.class, table.schema(), table.name());
            primaryKeys.put(table.key(), columns);
        }
        return primaryKeys;
    }

    // ------------------------------------------------------------------
    // Export / count
    // ------------------------------------------------------------------

    public long countTransferTable(TransferTable table) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM " + table.source(),
                Long.class);
        return count == null ? 0 : count;
    }

    /** {@code export_transfer_table}: rows ordered by primary key. */
    public List<Map<String, Object>> exportTransferTable(TransferTable table) {
        String orderBy = table.primaryKeyColumns().isEmpty() ? ""
                : table.primaryKeyColumns().stream()
                        .map(DataTransferModels::quoteIdentifier)
                        .reduce(" ORDER BY ", (a, b) -> a + ", " + b);
        return exportQuery("SELECT * FROM " + table.source() + orderBy);
    }

    public List<Map<String, Object>> exportQuery(String query) {
        List<String> rows = jdbc.queryForList(
                "SELECT row_to_json(t) FROM (" + query + ") t", String.class);
        List<Map<String, Object>> result = new ArrayList<>(rows.size());
        for (String row : rows) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> parsed = objectMapper.readValue(row, Map.class);
                result.add(parsed);
            } catch (Exception e) {
                throw ApiError.database(e.toString());
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // v2 insert path
    // ------------------------------------------------------------------

    /** {@code clear_transfer_tables}. */
    public void clearTransferTables(List<TransferTable> tables) {
        for (TransferTable table : tables) {
            jdbc.update("DELETE FROM " + table.source());
        }
    }

    /**
     * {@code insert_transfer_row}: unknown columns reject; generated columns
     * drop; the row goes in as bound jsonb via jsonb_populate_record.
     */
    public long insertTransferRow(TransferTable table, Map<String, Object> row) {
        for (String column : row.keySet()) {
            if (!table.columns().contains(column)) {
                throw ApiError.badRequest(table.table().key() + "." + column
                        + " does not exist in the destination schema");
            }
        }
        Map<String, Object> values = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            if (table.columns().contains(entry.getKey())
                    && !table.generatedColumns().contains(entry.getKey())) {
                values.put(entry.getKey(), entry.getValue());
            }
        }
        if (values.isEmpty()) {
            return 0;
        }
        String columns = values.keySet().stream()
                .map(DataTransferModels::quoteIdentifier)
                .reduce((a, b) -> a + ", " + b).orElseThrow();
        String quoted = table.table().quoted();
        String sql = "INSERT INTO " + quoted + " (" + columns + ")"
                + " OVERRIDING SYSTEM VALUE SELECT " + columns
                + " FROM jsonb_populate_record(NULL::" + quoted + ", ?::jsonb)"
                + " ON CONFLICT DO NOTHING";
        return jdbc.update(sql, toJson(values));
    }

    /** {@code SET CONSTRAINTS ALL DEFERRED}: checks move to COMMIT. */
    public void setConstraintsAllDeferred() {
        jdbc.execute("SET CONSTRAINTS ALL DEFERRED");
    }

    /**
     * {@code SET CONSTRAINTS ALL IMMEDIATE}: force the deferred foreign-key
     * checks while the transaction can still roll back. A violation surfaces
     * as the import's own 400, not a failed commit.
     */
    public void setConstraintsAllImmediate() {
        try {
            jdbc.execute("SET CONSTRAINTS ALL IMMEDIATE");
        } catch (org.springframework.dao.DataAccessException e) {
            throw ApiError.badRequest("Import failed referential-integrity checks: "
                    + e.getMessage()
                    + ". Tables outside the transferable set may still reference the data"
                    + " being overwritten.");
        }
    }

    /** {@code set_transfer_triggers}. */
    public void setTransferTriggers(List<TransferTable> tables, boolean enabled) {
        String action = enabled ? "ENABLE" : "DISABLE";
        for (TransferTable table : tables) {
            jdbc.update("ALTER TABLE " + table.table().quoted() + " " + action
                    + " TRIGGER USER");
        }
    }

    /**
     * {@code relax_foreign_keys}: make every immediate FK on `tables`
     * deferrable for the rest of the transaction, returning the ones changed
     * so {@link #restoreForeignKeys} can put them back.
     */
    public List<RelaxedForeignKey> relaxForeignKeys(List<TransferTable> tables) {
        List<String> keys = tables.stream().map(t -> t.table().key()).toList();
        List<RelaxedForeignKey> relaxed = jdbc.query("""
                SELECT namespace.nspname, child.relname, foreign_key.conname
                FROM pg_constraint foreign_key
                JOIN pg_class child ON child.oid = foreign_key.conrelid
                JOIN pg_namespace namespace ON namespace.oid = child.relnamespace
                WHERE foreign_key.contype = 'f'
                  AND NOT foreign_key.condeferrable
                  AND namespace.nspname || '.' || child.relname = ANY(?::text[])
                ORDER BY 1, 2, 3
                """, (rs, i) -> new RelaxedForeignKey(
                        new QualifiedTable(rs.getString(1), rs.getString(2)),
                        rs.getString(3)),
                (Object) keys.toArray(String[]::new));
        for (RelaxedForeignKey key : relaxed) {
            jdbc.update("ALTER TABLE " + key.table().quoted()
                    + " ALTER CONSTRAINT " + DataTransferModels.quoteIdentifier(key.constraint())
                    + " DEFERRABLE INITIALLY DEFERRED");
        }
        return relaxed;
    }

    /** {@code restore_foreign_keys}: return relaxed FKs to immediate checks. */
    public void restoreForeignKeys(List<RelaxedForeignKey> relaxed) {
        for (RelaxedForeignKey key : relaxed) {
            jdbc.update("ALTER TABLE " + key.table().quoted()
                    + " ALTER CONSTRAINT " + DataTransferModels.quoteIdentifier(key.constraint())
                    + " NOT DEFERRABLE INITIALLY IMMEDIATE");
        }
    }

    /** {@code reset_transfer_sequences}: per-column serial reset. */
    public void resetTransferSequences(List<TransferTable> tables) {
        for (TransferTable table : tables) {
            for (String column : table.columns()) {
                String sequence = jdbc.queryForObject(
                        "SELECT pg_get_serial_sequence(?, ?)", String.class,
                        table.table().key(), column);
                if (sequence == null) {
                    continue;
                }
                String source = table.source();
                String quotedColumn = DataTransferModels.quoteIdentifier(column);
                jdbc.update("SELECT setval(?::regclass,"
                        + " COALESCE((SELECT MAX(" + quotedColumn + ")::bigint FROM " + source
                        + "), 1), EXISTS (SELECT 1 FROM " + source + "))", sequence);
            }
        }
    }

    // ------------------------------------------------------------------
    // v1 legacy helpers
    // ------------------------------------------------------------------

    /** {@code clear_tables}. */
    public void clearTables(List<String> tables) {
        for (String table : tables) {
            jdbc.update("DELETE FROM " + table);
        }
    }

    public Set<Long> existingUserIds() {
        return new HashSet<>(jdbc.queryForList("SELECT id FROM users", Long.class));
    }

    /** {@code existing_ids}. */
    public Set<Long> existingIds(String table, List<Long> ids) {
        ensureKnownTable(table);
        if (ids.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(jdbc.queryForList("SELECT id FROM "
                + DataTransferModels.quoteIdentifier(table) + " WHERE id = ANY(?::bigint[])",
                Long.class, ids.toArray(Long[]::new)));
    }

    /** {@code room_ids_by_number}. */
    public Map<String, Long> roomIdsByNumber(List<String> roomNumbers) {
        if (roomNumbers.isEmpty()) {
            return Map.of();
        }
        Map<String, Long> result = new HashMap<>();
        jdbc.query("SELECT room_number, id FROM rooms WHERE room_number = ANY(?::text[])",
                rs -> {
                    while (rs.next()) {
                        result.put(rs.getString(1), rs.getLong(2));
                    }
                }, (Object) roomNumbers.toArray(String[]::new));
        return result;
    }

    public Map<String, Set<String>> tableColumns(List<String> tableNames) {
        return columnsByPredicate(tableNames, "");
    }

    public Map<String, Set<String>> requiredColumns(List<String> tableNames) {
        return columnsByPredicate(tableNames, " AND is_nullable = 'NO'");
    }

    public Map<String, Set<String>> generatedColumns(List<String> tableNames) {
        return columnsByPredicate(tableNames, " AND is_generated <> 'NEVER'");
    }

    private Map<String, Set<String>> columnsByPredicate(List<String> tableNames,
            String predicate) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String tableName : tableNames) {
            List<String> cols = jdbc.queryForList(
                    "SELECT column_name FROM information_schema.columns"
                            + " WHERE table_name = ? AND table_schema = 'public'" + predicate,
                    String.class, tableName);
            result.put(tableName, new HashSet<>(cols));
        }
        return result;
    }

    /** {@code user_fk_columns}: columns on each table referencing users. */
    public Map<String, Set<String>> userFkColumns(List<String> tableNames) {
        Map<String, Set<String>> result = new HashMap<>();
        for (String tableName : tableNames) {
            List<String> cols = jdbc.queryForList("""
                    SELECT kcu.column_name
                    FROM information_schema.table_constraints tc
                    JOIN information_schema.key_column_usage kcu
                      ON tc.constraint_name = kcu.constraint_name
                     AND tc.table_schema = kcu.table_schema
                    JOIN information_schema.constraint_column_usage ccu
                      ON ccu.constraint_name = tc.constraint_name
                     AND ccu.table_schema = tc.table_schema
                    WHERE tc.constraint_type = 'FOREIGN KEY'
                      AND tc.table_schema = 'public'
                      AND tc.table_name = ?
                      AND ccu.table_schema = 'public'
                      AND ccu.table_name = 'users'
                    """, String.class, tableName);
            result.put(tableName, new HashSet<>(cols));
        }
        return result;
    }

    /** {@code set_user_triggers}. */
    public void setUserTriggers(List<String> tables, boolean enabled) {
        String action = enabled ? "ENABLE" : "DISABLE";
        for (String table : tables) {
            ensureKnownTable(table);
            jdbc.update("ALTER TABLE " + table + " " + action + " TRIGGER USER");
        }
    }

    /** {@code align_status_constraints}: legacy status values → v2 enum. */
    public void alignStatusConstraints() {
        String[] statements = {
                "ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_status_check",
                "UPDATE bookings SET status = 'voided' WHERE status = 'cancelled'",
                "UPDATE bookings SET status = 'comp_void' WHERE status = 'comp_cancelled'",
                """
                ALTER TABLE bookings
                    ADD CONSTRAINT bookings_status_check
                    CHECK (status IN (
                        'pending', 'confirmed', 'checked_in', 'auto_checked_in', 'checked_out',
                        'no_show', 'completed', 'comp_void',
                        'partial_complimentary', 'fully_complimentary', 'voided'
                    ))
                """,
                "ALTER TABLE bookings DROP CONSTRAINT IF EXISTS bookings_payment_status_check",
                "UPDATE bookings SET payment_status = 'void' WHERE payment_status = 'cancelled'",
                """
                ALTER TABLE bookings
                    ADD CONSTRAINT bookings_payment_status_check
                    CHECK (payment_status IN (
                        'unpaid', 'unpaid_deposit', 'paid_rate', 'partial', 'paid', 'refunded', 'void'
                    ))
                """,
                "ALTER TABLE payments DROP CONSTRAINT IF EXISTS payments_status_check",
                "UPDATE payments SET status = 'void' WHERE status = 'cancelled'",
                """
                ALTER TABLE payments
                    ADD CONSTRAINT payments_status_check
                    CHECK (status IN ('pending', 'processing', 'completed', 'failed', 'refunded', 'void'))
                """,
                "ALTER TABLE invoices DROP CONSTRAINT IF EXISTS invoices_status_check",
                "UPDATE invoices SET status = 'void' WHERE status = 'cancelled'",
                """
                ALTER TABLE invoices
                    ADD CONSTRAINT invoices_status_check
                    CHECK (status IN ('draft', 'issued', 'paid', 'overdue', 'void', 'refunded'))
                """,
                "ALTER TABLE customer_ledgers DROP CONSTRAINT IF EXISTS valid_status",
                "ALTER TABLE customer_ledgers DROP CONSTRAINT IF EXISTS customer_ledgers_status_check",
                "UPDATE customer_ledgers SET status = 'void' WHERE status = 'cancelled'",
                """
                ALTER TABLE customer_ledgers
                    ADD CONSTRAINT valid_status
                    CHECK (status IN ('pending', 'partial', 'paid', 'overdue', 'void'))
                """,
        };
        for (String statement : statements) {
            jdbc.update(statement);
        }
    }

    /**
     * {@code insert_json_row} + {@code prepare_import_row}: generated and
     * unknown columns drop, missing user references normalize, the rest goes
     * in as bound jsonb.
     */
    public long insertJsonRow(String table, Map<String, Object> row,
            ImportRowPolicy policy, Set<String> auditUserFkColumns,
            Set<Long> existingUserIds) {
        Map<String, Object> values = new LinkedHashMap<>();
        List<String> columns = new ArrayList<>();
        for (Map.Entry<String, Object> entry : row.entrySet()) {
            String key = entry.getKey();
            if (policy.skipColumns().contains(key)
                    || (policy.validColumns() != null && !policy.validColumns().contains(key))) {
                continue;
            }
            Object importValue = entry.getValue();
            if (policy.userFkColumns().contains(key)) {
                importValue = normalizeUserFkValue(table, key, entry.getValue(),
                        policy.requiredColumns(), auditUserFkColumns, existingUserIds,
                        policy.fallbackUserId());
            }
            columns.add(key);
            values.put(key, importValue);
        }
        if (columns.isEmpty()) {
            return 0;
        }
        String columnList = columns.stream()
                .map(DataTransferModels::quoteIdentifier)
                .reduce((a, b) -> a + ", " + b).orElseThrow();
        String quotedTable = DataTransferModels.quoteIdentifier(table);
        String sql = "INSERT INTO " + quotedTable + " (" + columnList + ")"
                + " OVERRIDING SYSTEM VALUE SELECT " + columnList
                + " FROM jsonb_populate_record(NULL::" + quotedTable + ", ?::jsonb)"
                + " ON CONFLICT DO NOTHING";
        return jdbc.update(sql, toJson(values));
    }

    /** {@code normalize_user_fk_value}. */
    private static Object normalizeUserFkValue(String table, String column, Object value,
            Set<String> requiredColumns, Set<String> auditUserFkColumns,
            Set<Long> existingUserIds, long fallbackUserId) {
        Long userId = valueAsI64(value);
        if (userId == null || existingUserIds.contains(userId)) {
            return value;
        }
        boolean required = requiredColumns != null && requiredColumns.contains(column);
        if (required) {
            if (auditUserFkColumns.contains(column)) {
                return fallbackUserId;
            }
            throw ApiError.badRequest(table + "." + column + " references user id " + userId
                    + ", but that user does not exist in this database");
        }
        return null;
    }

    /** {@code reset_sequences}: the v1 id-column reset block. */
    public void resetSequences(List<String> tables) {
        for (String table : tables) {
            jdbc.update(resetSequenceSql(table));
        }
    }

    /** {@code reset_sequence_sql}. */
    static String resetSequenceSql(String table) {
        String quotedTable = DataTransferModels.quoteIdentifier(table);
        String tableRegclass = "'" + "public." + table.replace("'", "''") + "'";
        String tableName = "'" + table.replace("'", "''") + "'";
        String maxIdQuery = "'SELECT MAX(id)::bigint FROM " + quotedTable.replace("'", "''") + "'";
        return """
                DO $$
                DECLARE
                    sequence_name text;
                    max_id bigint;
                BEGIN
                    SELECT COALESCE(
                        pg_get_serial_sequence(%s, 'id'),
                        (
                            SELECT substring(column_default FROM 'nextval\\(''([^'']+)''::regclass\\)')
                            FROM information_schema.columns
                            WHERE table_schema = 'public'
                              AND table_name = %s
                              AND column_name = 'id'
                        )
                    ) INTO sequence_name;

                    IF sequence_name IS NULL THEN
                        RETURN;
                    END IF;

                    EXECUTE %s INTO max_id;

                    IF max_id IS NULL THEN
                        PERFORM setval(sequence_name::regclass, 1, false);
                    ELSE
                        PERFORM setval(sequence_name::regclass, GREATEST(max_id, 1), true);
                    END IF;
                END $$;
                """.formatted(tableRegclass, tableName, maxIdQuery);
    }

    // ------------------------------------------------------------------
    // transfer_order — deterministic topological sort with cycle breaking
    // ------------------------------------------------------------------

    /**
     * {@code transfer_order}: parents before children among the selected
     * tables. When the remaining graph is cyclic, only a table genuinely on a
     * cycle may be forced out — the one with the fewest outstanding
     * dependencies, ties broken lexicographically — so edges outside the cycle
     * still order parents first.
     */
    public static List<String> transferOrder(List<String> selected,
            Map<String, Set<String>> dependencies) {
        Set<String> selectedSet = new TreeSet<>(selected);
        Map<String, TreeSet<String>> unresolved = new HashMap<>();
        for (String table : selectedSet) {
            TreeSet<String> deps = new TreeSet<>();
            for (String dependency : dependencies.getOrDefault(table, Set.of())) {
                if (selectedSet.contains(dependency)) {
                    deps.add(dependency);
                }
            }
            unresolved.put(table, deps);
        }
        List<String> ordered = new ArrayList<>(selectedSet.size());

        while (!unresolved.isEmpty()) {
            List<String> ready = new ArrayList<>();
            for (Map.Entry<String, TreeSet<String>> entry : unresolved.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    ready.add(entry.getKey());
                }
            }
            ready.sort(String::compareTo);
            if (ready.isEmpty()) {
                String victim = null;
                for (String table : unresolved.keySet()) {
                    if (!reachesItself(table, unresolved)) {
                        continue;
                    }
                    if (victim == null
                            || unresolved.get(table).size() < unresolved.get(victim).size()
                            || (unresolved.get(table).size() == unresolved.get(victim).size()
                                    && table.compareTo(victim) < 0)) {
                        victim = table;
                    }
                }
                if (victim == null) {
                    throw ApiError.badRequest(
                            "Selected transfer tables contain a circular"
                                    + " foreign-key dependency");
                }
                ready.add(victim);
            }
            for (String table : ready) {
                unresolved.remove(table);
                for (TreeSet<String> deps : unresolved.values()) {
                    deps.remove(table);
                }
                ordered.add(table);
            }
        }
        return ordered;
    }

    /** {@code reaches_itself}: whether `start` sits on a cycle. */
    private static boolean reachesItself(String start,
            Map<String, TreeSet<String>> unresolved) {
        List<String> stack = new ArrayList<>(unresolved.getOrDefault(start, new TreeSet<>()));
        Set<String> seen = new HashSet<>();
        while (!stack.isEmpty()) {
            String table = stack.remove(stack.size() - 1);
            if (table.equals(start)) {
                return true;
            }
            if (!seen.add(table)) {
                continue;
            }
            stack.addAll(unresolved.getOrDefault(table, new TreeSet<>()));
        }
        return false;
    }

    static Long valueAsI64(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string) {
            try {
                return Long.parseLong(string);
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private String toJson(Map<String, Object> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception e) {
            throw ApiError.database(e.toString());
        }
    }
}
