package com.hotelapp.datatransfer;

import com.hotelapp.auth.AuthService;
import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGateHelper;
import com.hotelapp.datatransfer.DataTransferModels.ExportPreview;
import com.hotelapp.datatransfer.DataTransferModels.FullDataExport;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code routes/data_transfer.rs}: export/preview ride on the grantable
 * {@code settings:manage}; import can clear whole tables so it stays behind
 * the super-admin flag on top of that permission.
 */
@RestController
public class DataTransferController {

    private final DataTransferService service;
    private final AuthService authService;
    private final AuditWriter audit;

    public DataTransferController(DataTransferService service, AuthService authService,
            AuditWriter audit) {
        this.service = service;
        this.authService = authService;
        this.audit = audit;
    }

    @GetMapping("/api/data-transfer/export/preview")
    public Map<String, Object> previewExport() {
        PermissionGateHelper.check(CurrentUser.require().userId(), "settings:manage");
        ExportPreview preview = service.previewExportCounts();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("generated_at", preview.generatedAt());
        body.put("counts", preview.counts());
        body.put("total_records", preview.totalRecords());
        body.put("tables", preview.tables());
        return body;
    }

    @GetMapping("/api/data-transfer/export")
    public FullDataExport exportData() {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "settings:manage");
        FullDataExport export = service.exportBookingData();
        // This single GET returns every guest, booking, payment and ledger
        // row — the largest exfiltration channel in the product. Audited after
        // a successful export so the counts describe what actually left.
        audit.event(userId, "data_export", "data_transfer", null, Map.of(
                "table_count", export.tables().size(),
                "record_count", export.tables().values().stream()
                        .mapToLong(java.util.List::size).sum()));
        return export;
    }

    @PostMapping("/api/data-transfer/import")
    public Map<String, Object> importData(@RequestBody Map<String, Object> request) {
        long userId = CurrentUser.require().userId();
        PermissionGateHelper.check(userId, "settings:manage");
        if (!authService.isSuperAdmin(userId)) {
            throw ApiError.forbidden(
                    "Only super administrators can perform this operation");
        }
        return service.importBookingData(userId, request);
    }
}
