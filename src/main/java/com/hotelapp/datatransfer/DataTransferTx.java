package com.hotelapp.datatransfer;

import com.hotelapp.core.error.ApiError;
import com.hotelapp.datatransfer.DataTransferModels.ImportRowPolicy;
import com.hotelapp.datatransfer.DataTransferModels.RelaxedForeignKey;
import com.hotelapp.datatransfer.DataTransferModels.TransferTable;
import com.hotelapp.datatransfer.DataTransferService.RoomReferenceResolver;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The two {@code import_booking_data} transaction bodies, ported from
 * {@code services/data_transfer.rs}: the v1 legacy payload path and the v2
 * schema-driven path. Everything inside either method commits or rolls back
 * as one unit.
 */
@Component
public class DataTransferTx {

    private static final Logger log = LoggerFactory.getLogger(DataTransferTx.class);

    private final DataTransferRepo repo;

    public DataTransferTx(DataTransferRepo repo) {
        this.repo = repo;
    }

    // ------------------------------------------------------------------
    // v1 legacy payload
    // ------------------------------------------------------------------

    /** {@code import_legacy_booking_data}'s transaction body. */
    @Transactional
    public Map<String, Object> importLegacyBookingData(long importUserId,
            boolean overwrite, Map<String, Object> data, List<String> requestedTables) {
        Map<String, Set<String>> generatedColumns =
                new HashMap<>(DataTransferService.baseGeneratedColumns());
        Set<Long> existingUserIds = repo.existingUserIds();
        Map<String, Set<String>> tableColumns = repo.tableColumns(
                DataTransferService.ALL_IMPORT_TABLES);
        Map<String, Set<String>> requiredColumns = repo.requiredColumns(
                DataTransferService.ALL_IMPORT_TABLES);
        Map<String, Set<String>> userFkColumns = repo.userFkColumns(
                DataTransferService.ALL_IMPORT_TABLES);
        for (Map.Entry<String, Set<String>> entry : repo.generatedColumns(
                DataTransferService.ALL_IMPORT_TABLES).entrySet()) {
            generatedColumns.computeIfAbsent(entry.getKey(), k -> new HashSet<>())
                    .addAll(entry.getValue());
        }

        // Foreign-key-safe insert order; the import loop and overwrite clear
        // both derive from it so a table never lands before its parents.
        Map<String, List<Map<String, Object>>> tablesAndData = new LinkedHashMap<>();
        for (String table : DataTransferService.ALL_IMPORT_TABLES) {
            tablesAndData.put(table, DataTransferService.rowList(data.get(table)));
        }

        Set<String> selectedTables = DataTransferService.selectedImportTables(
                requestedTables, tablesAndData);
        if (overwrite) {
            DataTransferService.expandOverwriteClearTables(selectedTables);
        }

        if (overwrite) {
            // Clear selected tables in reverse (child-before-parent) order. The
            // UI sends this list explicitly so an overwrite can intentionally
            // restore a table to empty rows.
            List<String> order = new ArrayList<>(tablesAndData.keySet());
            List<String> clearTables = new ArrayList<>();
            for (int i = order.size() - 1; i >= 0; i--) {
                if (selectedTables.contains(order.get(i))) {
                    clearTables.add(order.get(i));
                }
            }
            try {
                repo.clearTables(clearTables);
            } catch (ApiError | org.springframework.dao.DataAccessException e) {
                String errorDetail = e instanceof ApiError apiError
                        ? DataTransferService.importErrorDetail(apiError) : e.getMessage();
                String message = "Overwrite failed while clearing selected data: "
                        + errorDetail
                        + ". Include dependent tables in the overwrite selection or remove"
                        + " the blocked references before retrying. No changes were saved.";
                log.warn("{}", message);
                throw ApiError.badRequest(message);
            }
            log.info("Phase 1: cleared {} table(s) for overwrite", clearTables.size());
        }

        RoomReferenceResolver roomReferences = RoomReferenceResolver.build(repo,
                selectedTables, tablesAndData.get("rooms"));
        validateRoomReferences(selectedTables, tablesAndData, roomReferences);

        repo.alignStatusConstraints();
        repo.setUserTriggers(DataTransferService.TABLES_WITH_TRIGGERS, false);

        Map<String, Object> counts = new LinkedHashMap<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : tablesAndData.entrySet()) {
            String table = entry.getKey();
            if (!selectedTables.contains(table)) {
                continue;
            }
            Set<String> skip = generatedColumns.getOrDefault(table, Set.of());
            long inserted = 0;
            List<Map<String, Object>> rows = entry.getValue();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> obj = rows.get(rowIndex);
                if (obj == null) {
                    String message = "Import failed for table " + table + " row "
                            + (rowIndex + 1)
                            + " because the row is not a JSON object. No changes were saved.";
                    log.warn("{}", message);
                    throw ApiError.badRequest(message);
                }
                Map<String, Object> effective = DataTransferService
                        .roomReferenceColumns(table) != null
                        ? DataTransferService.remapRoomReferences(table, obj, roomReferences)
                        : obj;
                try {
                    long affected = repo.insertJsonRow(table, effective,
                            new ImportRowPolicy(skip, tableColumns.get(table),
                                    requiredColumns.get(table),
                                    userFkColumns.getOrDefault(table, Set.of()),
                                    importUserId),
                            DataTransferService.AUDIT_USER_FK_COLUMNS, existingUserIds);
                    if (affected > 0) {
                        inserted++;
                    }
                } catch (ApiError | org.springframework.dao.DataAccessException e) {
                    String errorDetail = e instanceof ApiError apiError
                            ? DataTransferService.importErrorDetail(apiError) : e.getMessage();
                    String message = "Import failed for table " + table + " row "
                            + (rowIndex + 1)
                            + DataTransferService.rowReference(effective) + ": " + errorDetail
                            + ". No changes were saved.";
                    log.warn("{}", message);
                    throw ApiError.badRequest(message);
                }
            }
            counts.put(table, inserted);
            if (inserted > 0) {
                log.info("Inserted {} rows into {}", inserted, table);
            }
        }

        repo.setUserTriggers(DataTransferService.TABLES_WITH_TRIGGERS, true);
        List<String> sequenceResetTables = DataTransferRepo.TABLE_INSERT_ORDER.stream()
                .filter(table -> !DataTransferService.COMPOSITE_PK_TABLES.contains(table))
                .toList();
        repo.resetSequences(sequenceResetTables);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("mode", overwrite ? "overwrite" : "import");
        response.put("records_imported", counts);
        return response;
    }

    /** {@code validate_room_references}. */
    private void validateRoomReferences(Set<String> selectedTables,
            Map<String, List<Map<String, Object>>> tablesAndData,
            RoomReferenceResolver roomReferences) {
        List<Long> roomIds = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Map.Entry<String, List<Map<String, Object>>> entry : tablesAndData.entrySet()) {
            if (!selectedTables.contains(entry.getKey())) {
                continue;
            }
            String[] columns = DataTransferService.roomReferenceColumns(entry.getKey());
            if (columns == null) {
                continue;
            }
            for (Map<String, Object> obj : entry.getValue()) {
                if (obj == null) {
                    continue;
                }
                for (String column : columns) {
                    Long roomId = DataTransferRepo.valueAsI64(obj.get(column));
                    if (roomId != null) {
                        long resolved = roomReferences.resolveRoomId(roomId);
                        if (seen.add(resolved)) {
                            roomIds.add(resolved);
                        }
                    }
                }
            }
        }
        Set<Long> existingRoomIds = repo.existingIds("rooms", roomIds);
        for (Map.Entry<String, List<Map<String, Object>>> entry : tablesAndData.entrySet()) {
            String table = entry.getKey();
            if (!selectedTables.contains(table)) {
                continue;
            }
            String[] columns = DataTransferService.roomReferenceColumns(table);
            if (columns == null) {
                continue;
            }
            List<Map<String, Object>> rows = entry.getValue();
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> obj = rows.get(rowIndex);
                if (obj == null) {
                    continue;
                }
                for (String column : columns) {
                    Long roomId = DataTransferRepo.valueAsI64(obj.get(column));
                    if (roomId == null) {
                        continue;
                    }
                    long resolved = roomReferences.resolveRoomId(roomId);
                    if (!roomReferences.containsImportedRoomId(roomId)
                            && !existingRoomIds.contains(resolved)) {
                        throw ApiError.badRequest("Import failed for table " + table
                                + " row " + (rowIndex + 1)
                                + DataTransferService.rowReference(obj) + ": " + column
                                + " references room id " + roomId
                                + ", but that room is not present in the import file and"
                                + " does not exist in this database. Include Rooms in the"
                                + " import file, import a full backup, or create the missing"
                                + " room before retrying. No changes were saved.");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // v2 schema-driven payload
    // ------------------------------------------------------------------

    /**
     * {@code import_full_data}'s transaction body: relax the selected tables'
     * foreign keys, disable their triggers, defer the checks, clear and
     * insert, then force the checks while the transaction is still ours to
     * roll back.
     */
    @Transactional
    public Map<String, Object> importFullData(boolean overwrite,
            List<TransferTable> orderedTables,
            Map<String, List<Map<String, Object>>> dataTables) {
        List<RelaxedForeignKey> relaxed = repo.relaxForeignKeys(orderedTables);
        repo.setTransferTriggers(orderedTables, false);

        // Every ALTER above has to happen before anything queues a deferred
        // trigger event — PostgreSQL refuses to alter a table that has pending
        // ones.
        repo.setConstraintsAllDeferred();

        if (overwrite) {
            List<TransferTable> clearTables = new ArrayList<>(orderedTables);
            java.util.Collections.reverse(clearTables);
            repo.clearTransferTables(clearTables);
        }

        Map<String, Object> counts = new LinkedHashMap<>();
        for (TransferTable table : orderedTables) {
            String name = table.table().key();
            List<Map<String, Object>> rows =
                    dataTables.getOrDefault(name, List.of());
            long inserted = 0;
            for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
                Map<String, Object> object = rows.get(rowIndex);
                if (object == null) {
                    throw ApiError.badRequest("Import failed for table " + name + " row "
                            + (rowIndex + 1)
                            + " because the row is not a JSON object");
                }
                inserted += repo.insertTransferRow(table, object);
            }
            counts.put(name, inserted);
        }

        // Deferring moved the foreign-key checks to COMMIT. Force them now so
        // a violation fails the import instead of surfacing as a failed commit.
        repo.setConstraintsAllImmediate();

        repo.setTransferTriggers(orderedTables, true);
        repo.restoreForeignKeys(relaxed);
        repo.resetTransferSequences(orderedTables);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("mode", overwrite ? "overwrite" : "import");
        response.put("records_imported", counts);
        return response;
    }
}
