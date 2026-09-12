package com.hotelapp.datatransfer;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.datatransfer.DataTransferModels.ExportPreview;
import com.hotelapp.datatransfer.DataTransferModels.FullDataExport;
import com.hotelapp.datatransfer.DataTransferModels.QualifiedTable;
import com.hotelapp.datatransfer.DataTransferModels.TransferTable;
import com.hotelapp.datatransfer.DataTransferModels.TransferTablePreview;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Port of {@code services/data_transfer.rs} — preview, export, and the import
 * orchestration shared by the v1 legacy and v2 schema-driven payload paths.
 */
@Service
public class DataTransferService {

    /** {@code ALL_IMPORT_TABLES} — same list as TABLE_INSERT_ORDER. */
    static final List<String> ALL_IMPORT_TABLES = DataTransferRepo.TABLE_INSERT_ORDER;

    /** {@code COMPOSITE_PK_TABLES} — no serial id; skip sequence resets. */
    static final Set<String> COMPOSITE_PK_TABLES = Set.of(
            "room_type_amenities", "room_status_transitions", "promotion_room_types");

    /** {@code TABLES_WITH_TRIGGERS}. */
    static final List<String> TABLES_WITH_TRIGGERS = List.of(
            "bookings", "rooms", "guests", "customer_ledgers", "payments");

    /** {@code ROOM_REFERENCE_COLUMNS}. */
    static final Map<String, String[]> ROOM_REFERENCE_COLUMNS = Map.of(
            "bookings", new String[]{"room_id"},
            "room_history", new String[]{"room_id"},
            "housekeeping_tasks", new String[]{"room_id"},
            "maintenance_tickets", new String[]{"room_id"},
            "room_changes", new String[]{"from_room_id", "to_room_id"},
            "room_status_change_log", new String[]{"room_id"});

    /** {@code AUDIT_USER_FK_COLUMNS}. */
    static final Set<String> AUDIT_USER_FK_COLUMNS = Set.of(
            "created_by", "updated_by", "cancelled_by", "posted_by", "modified_by",
            "run_by", "changed_by", "processed_by", "cashier_id", "void_by",
            "delivered_by", "inspected_by", "assigned_to", "reported_by",
            "linked_by", "verified_by", "response_by");

    /**
     * {@code OVERWRITE_DELETE_DEPENDENCIES}: child -> parent pairs whose
     * overwrite clear must expand through dependents.
     */
    static final List<String[]> OVERWRITE_DELETE_DEPENDENCIES = List.of(
            new String[]{"rooms", "room_types"},
            new String[]{"bookings", "companies"},
            new String[]{"bookings", "guests"},
            new String[]{"bookings", "rooms"},
            new String[]{"bookings", "booking_channels"},
            new String[]{"promotion_room_types", "promotions"},
            new String[]{"promotion_room_types", "room_types"},
            new String[]{"vouchers", "promotions"},
            new String[]{"vouchers", "guests"},
            new String[]{"voucher_redemptions", "vouchers"},
            new String[]{"voucher_redemptions", "promotions"},
            new String[]{"voucher_redemptions", "bookings"},
            new String[]{"voucher_redemptions", "guests"},
            new String[]{"voucher_redemption_allocations", "voucher_redemptions"},
            new String[]{"voucher_redemption_allocations", "bookings"},
            new String[]{"booking_guests", "bookings"},
            new String[]{"booking_modifications", "bookings"},
            new String[]{"booking_history", "bookings"},
            new String[]{"payments", "bookings"},
            new String[]{"invoices", "bookings"},
            new String[]{"customer_ledger_payments", "customer_ledgers"},
            new String[]{"night_audit_details", "night_audit_runs"},
            new String[]{"room_changes", "bookings"},
            new String[]{"room_changes", "rooms"},
            new String[]{"user_guests", "guests"},
            new String[]{"guest_complimentary_credits", "guests"},
            new String[]{"guest_complimentary_credits", "room_types"},
            new String[]{"room_rates", "rate_plans"},
            new String[]{"room_rates", "room_types"},
            new String[]{"room_type_amenities", "amenities"},
            new String[]{"room_type_amenities", "room_types"},
            new String[]{"loyalty_tiers", "loyalty_programs"},
            new String[]{"loyalty_memberships", "guests"},
            new String[]{"loyalty_memberships", "loyalty_programs"},
            new String[]{"loyalty_memberships", "loyalty_tiers"},
            new String[]{"points_transactions", "loyalty_memberships"},
            new String[]{"reward_catalog", "loyalty_programs"},
            new String[]{"reward_redemptions", "loyalty_memberships"},
            new String[]{"reward_redemptions", "reward_catalog"},
            new String[]{"corporate_account_contacts", "corporate_accounts"},
            new String[]{"booking_services", "bookings"},
            new String[]{"booking_services", "services"},
            new String[]{"room_history", "rooms"},
            new String[]{"room_status_change_log", "rooms"},
            new String[]{"loyalty_members", "guests"},
            new String[]{"loyalty_accounts", "loyalty_members"},
            new String[]{"loyalty_accounts", "loyalty_tiers"},
            new String[]{"loyalty_rewards", "loyalty_tiers"},
            new String[]{"loyalty_transactions", "loyalty_members"},
            new String[]{"loyalty_transactions", "loyalty_accounts"},
            new String[]{"loyalty_redemptions", "loyalty_members"},
            new String[]{"loyalty_redemptions", "loyalty_rewards"},
            new String[]{"loyalty_redemptions", "loyalty_transactions"},
            new String[]{"housekeeping_tasks", "rooms"},
            new String[]{"guest_documents", "guests"},
            new String[]{"guest_notes", "guests"},
            new String[]{"guest_preferences", "guests"},
            new String[]{"guest_reviews", "guests"},
            new String[]{"self_checkin_events", "bookings"},
            new String[]{"night_audit_posted_nights", "bookings"});

    private final DataTransferRepo repo;
    private final DataTransferTx tx;

    public DataTransferService(DataTransferRepo repo, DataTransferTx tx) {
        this.repo = repo;
        this.tx = tx;
    }

    /**
     * {@code is_transferable_key}: only `public` business-data tables cross
     * the boundary — users/roles/sessions/audit carry credentials and grant
     * state, so exporting them would hand a settings:manage holder every
     * password hash and TOTP seed.
     */
    public static boolean isTransferableKey(String key) {
        try {
            QualifiedTable table = QualifiedTable.parse(key);
            return "public".equals(table.schema()) && ALL_IMPORT_TABLES.contains(table.name());
        } catch (ApiError e) {
            return false;
        }
    }

    /** {@code preview_export_counts}. */
    public ExportPreview previewExportCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        long totalRecords = 0;
        List<TransferTablePreview> tables = new ArrayList<>();
        for (TransferTable table : repo.transferTables()) {
            if (!isTransferableKey(table.table().key())) {
                continue;
            }
            long count = repo.countTransferTable(table);
            String name = table.table().key();
            counts.put(name, count);
            totalRecords += count;
            List<String> dependencies = new ArrayList<>(table.dependencies());
            dependencies.sort(String::compareTo);
            tables.add(new TransferTablePreview(name, count, dependencies));
        }
        return new ExportPreview(Instant.now().toString(), counts, totalRecords, tables);
    }

    /** {@code export_booking_data}. */
    public FullDataExport exportBookingData() {
        Map<String, List<Map<String, Object>>> tables = new java.util.TreeMap<>();
        for (TransferTable table : repo.transferTables()) {
            if (!isTransferableKey(table.table().key())) {
                continue;
            }
            tables.put(table.table().key(), repo.exportTransferTable(table));
        }
        return new FullDataExport("2.0", Instant.now().toString(), tables);
    }

    /**
     * {@code import_booking_data}: `data.tables` present routes to the v2
     * schema-driven path; otherwise the v1 legacy shape. {@code mode} accepts
     * `import` or `overwrite` like upstream's snake_case enum.
     */
    public Map<String, Object> importBookingData(long importUserId,
            Map<String, Object> request) {
        Object modeRaw = request.get("mode");
        Object dataRaw = request.get("data");
        if (!(modeRaw instanceof String mode) || !(dataRaw instanceof Map)) {
            throw ApiError.unprocessableEntity("mode and data are required");
        }
        boolean overwrite;
        switch (mode) {
            case "import" -> overwrite = false;
            case "overwrite" -> overwrite = true;
            default -> throw ApiError.unprocessableEntity(
                    "unknown variant `" + mode + "`, expected `import` or `overwrite`");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) dataRaw;
        List<String> requestedTables = stringList(request.get("tables"));

        if (data.get("tables") instanceof Map) {
            return importFullData(overwrite, data, requestedTables);
        }
        return tx.importLegacyBookingData(importUserId, overwrite, data, requestedTables);
    }

    /**
     * {@code import_full_data}'s pre-transaction half: version, transferable
     * and existence checks, dependency expansion for overwrite, then the
     * deterministic order — the tx body only sees ordered descriptors.
     */
    private Map<String, Object> importFullData(boolean overwrite,
            Map<String, Object> data, List<String> requestedTables) {
        Object versionRaw = data.get("version");
        String version = versionRaw instanceof String s ? s : null;
        if (!"2.0".equals(version)) {
            throw ApiError.badRequest("Unsupported schema-driven transfer version '"
                    + version + "'");
        }
        Map<String, List<Map<String, Object>>> dataTables = v2Tables(data);

        List<TransferTable> descriptors = repo.transferTables();
        Map<String, TransferTable> descriptorByName = new HashMap<>();
        for (TransferTable descriptor : descriptors) {
            descriptorByName.put(descriptor.table().key(), descriptor);
        }

        for (String table : dataTables.keySet()) {
            QualifiedTable.parse(table);
            if (!isTransferableKey(table)) {
                throw ApiError.badRequest("Transfer table '" + table
                        + "' is not permitted: only the business-data table set"
                        + " can be imported");
            }
            if (!descriptorByName.containsKey(table)) {
                throw ApiError.badRequest("Transfer table '" + table
                        + "' does not exist in the destination schema");
            }
        }

        Set<String> selected = requestedTables.isEmpty()
                ? new HashSet<>(dataTables.keySet())
                : new HashSet<>(requestedTables);
        for (String table : selected) {
            QualifiedTable.parse(table);
            if (!isTransferableKey(table)) {
                throw ApiError.badRequest("Transfer table '" + table
                        + "' is not permitted: only the business-data table set"
                        + " can be imported");
            }
            if (!dataTables.containsKey(table)) {
                throw ApiError.badRequest("Selected transfer table '" + table
                        + "' is missing from the import file");
            }
            if (!descriptorByName.containsKey(table)) {
                throw ApiError.badRequest("Unknown transfer table '" + table
                        + "' was requested");
            }
        }

        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Map.Entry<String, TransferTable> entry : descriptorByName.entrySet()) {
            dependencies.put(entry.getKey(), entry.getValue().dependencies());
        }
        if (overwrite) {
            expandFullOverwriteTables(selected, dependencies);
        }
        List<String> order = DataTransferRepo.transferOrder(
                new ArrayList<>(selected), dependencies);
        List<TransferTable> orderedTables = new ArrayList<>(order.size());
        for (String name : order) {
            orderedTables.add(descriptorByName.get(name));
        }
        return tx.importFullData(overwrite, orderedTables, dataTables);
    }

    /** {@code FullDataExport.tables} rows from the raw map. */
    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> v2Tables(
            Map<String, Object> data) {
        Object tables = data.get("tables");
        if (!(tables instanceof Map)) {
            throw ApiError.badRequest("data.tables must be an object");
        }
        Map<String, List<Map<String, Object>>> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : ((Map<String, Object>) tables).entrySet()) {
            result.put(entry.getKey(), rowList(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    static List<Map<String, Object>> rowList(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            if (item instanceof Map) {
                rows.add((Map<String, Object>) item);
            } else {
                rows.add(null); // positional marker; validated per-row downstream
            }
        }
        return rows;
    }

    @SuppressWarnings("unchecked")
    static List<String> stringList(Object value) {
        if (!(value instanceof List)) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        for (Object item : (List<Object>) value) {
            if (item instanceof String s) {
                result.add(s);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // Pure helpers (ported verbatim — unit-tested)
    // ------------------------------------------------------------------

    /**
     * {@code expand_full_overwrite_tables}: children of selected parents join
     * the clear set until the graph closes, so old exports can clear a parent
     * without orphaning dependents.
     */
    static void expandFullOverwriteTables(Set<String> selected,
            Map<String, Set<String>> dependencies) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Map.Entry<String, Set<String>> entry : dependencies.entrySet()) {
                boolean parentSelected = entry.getValue().stream().anyMatch(selected::contains);
                if (parentSelected && isTransferableKey(entry.getKey())
                        && selected.add(entry.getKey())) {
                    changed = true;
                }
            }
        }
    }

    /** {@code base_generated_columns}. */
    static Map<String, Set<String>> baseGeneratedColumns() {
        Map<String, Set<String>> columns = new HashMap<>();
        columns.put("bookings",
                Set.of("nights", "total_guests", "tourism_billable_amount"));
        columns.put("invoices", Set.of("balance_due"));
        columns.put("customer_ledgers", Set.of("balance_due"));
        return columns;
    }

    /** {@code row_reference}: one identifying `(key: value)` for messages. */
    static String rowReference(Map<String, Object> row) {
        for (String key : List.of("id", "booking_number", "invoice_number",
                "room_number", "company_name", "full_name", "audit_date")) {
            Object value = row.get(key);
            if (value != null) {
                String rendered = value instanceof String s ? s : String.valueOf(value);
                return " (" + key + ": " + rendered + ")";
            }
        }
        return "";
    }

    /** {@code RoomReferenceResolver}. */
    static final class RoomReferenceResolver {
        private final Map<Long, Long> importedRoomIds;

        RoomReferenceResolver(Map<Long, Long> importedRoomIds) {
            this.importedRoomIds = importedRoomIds;
        }

        static RoomReferenceResolver build(DataTransferRepo repo,
                Set<String> selectedTables, List<Map<String, Object>> importedRooms) {
            Map<Long, Long> importedRoomIds = new HashMap<>();
            if (!selectedTables.contains("rooms")) {
                return new RoomReferenceResolver(importedRoomIds);
            }
            List<Object[]> refs = importedRoomRefs(importedRooms);
            List<String> wanted = refs.stream().map(r -> (String) r[1])
                    .filter(java.util.Objects::nonNull).toList();
            Map<String, Long> existingByNumber = repo.roomIdsByNumber(wanted);
            for (Object[] ref : refs) {
                Long importedId = (Long) ref[0];
                String number = (String) ref[1];
                Long resolved = number == null ? null : existingByNumber.get(number);
                importedRoomIds.put(importedId, resolved != null ? resolved : importedId);
            }
            return new RoomReferenceResolver(importedRoomIds);
        }

        long resolveRoomId(long roomId) {
            return importedRoomIds.getOrDefault(roomId, roomId);
        }

        boolean containsImportedRoomId(long roomId) {
            return importedRoomIds.containsKey(roomId);
        }
    }

    /** {@code imported_room_refs}. */
    static List<Object[]> importedRoomRefs(List<Map<String, Object>> importedRooms) {
        List<Object[]> refs = new ArrayList<>();
        for (Map<String, Object> row : importedRooms) {
            if (row == null) {
                continue;
            }
            Long id = DataTransferRepo.valueAsI64(row.get("id"));
            if (id == null) {
                continue;
            }
            Object number = row.get("room_number");
            refs.add(new Object[]{id, number instanceof String s ? s : null});
        }
        return refs;
    }

    /** {@code room_reference_columns}. */
    static String[] roomReferenceColumns(String table) {
        return ROOM_REFERENCE_COLUMNS.get(table);
    }

    /** {@code remap_room_references}. */
    static Map<String, Object> remapRoomReferences(String table,
            Map<String, Object> row, RoomReferenceResolver resolver) {
        String[] columns = roomReferenceColumns(table);
        if (columns == null) {
            return row;
        }
        Map<String, Object> remapped = new LinkedHashMap<>(row);
        boolean changed = false;
        for (String column : columns) {
            Long roomId = DataTransferRepo.valueAsI64(row.get(column));
            if (roomId == null) {
                continue;
            }
            long resolved = resolver.resolveRoomId(roomId);
            if (resolved != roomId) {
                remapped.put(column, resolved);
                changed = true;
            }
        }
        return changed ? remapped : row;
    }

    /** {@code selected_import_tables}. */
    static Set<String> selectedImportTables(List<String> requestedTables,
            Map<String, List<Map<String, Object>>> tablesAndData) {
        if (requestedTables.isEmpty()) {
            Set<String> selected = new HashSet<>();
            for (Map.Entry<String, List<Map<String, Object>>> entry : tablesAndData.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    selected.add(entry.getKey());
                }
            }
            return selected;
        }
        Set<String> selected = new HashSet<>();
        for (String table : requestedTables) {
            if (!tablesAndData.containsKey(table)) {
                throw ApiError.badRequest(
                        "Unknown import table '" + table + "' was requested");
            }
            selected.add(table);
        }
        return selected;
    }

    /** {@code expand_overwrite_clear_tables}. */
    static void expandOverwriteClearTables(Set<String> selectedTables) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (String[] pair : OVERWRITE_DELETE_DEPENDENCIES) {
                if (selectedTables.contains(pair[1]) && selectedTables.add(pair[0])) {
                    changed = true;
                }
            }
        }
    }

    /** {@code import_error_detail}. */
    static String importErrorDetail(ApiError error) {
        return switch (error.kind()) {
            case BAD_REQUEST, CONFLICT, DATABASE -> error.message();
            default -> error.toString();
        };
    }
}
