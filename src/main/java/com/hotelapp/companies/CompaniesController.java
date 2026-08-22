package com.hotelapp.companies;

import com.hotelapp.core.audit.AuditWriter;
import com.hotelapp.core.error.ApiError;
import com.hotelapp.core.security.CurrentUser;
import com.hotelapp.core.security.PermissionGate;
import static com.hotelapp.rates.RatesController.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CompaniesController {

    private final JdbcTemplate jdbc;
    private final PermissionGate gate;
    private final AuditWriter audit;

    public CompaniesController(JdbcTemplate jdbc, PermissionGate gate, AuditWriter audit) {
        this.jdbc = jdbc;
        this.gate = gate;
        this.audit = audit;
    }

    @GetMapping("/api/companies")
    public List<Map<String, Object>> list() {
        gate.check(CurrentUser.require().userId(), "companies:read");
        return jdbc.queryForList("SELECT * FROM companies ORDER BY name");
    }

    @PostMapping("/api/companies")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "companies:manage");
        String name = str(body, "name");
        if (name == null) {
            throw ApiError.badRequest("Company name is required");
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO companies (name, contact_person, email, phone, address,
                    is_active, created_by)
                VALUES (?, ?, ?, ?, ?, true, ?) RETURNING id
                """, Long.class, name, str(body, "contact_person"),
                str(body, "email"), str(body, "phone"),
                str(body, "address"), userId);
        audit.event(userId, "company_created", "company", id, null);
        return one(id);
    }

    @GetMapping("/api/companies/{id}")
    public Map<String, Object> get(@PathVariable long id) {
        gate.check(CurrentUser.require().userId(), "companies:read");
        return one(id);
    }

    @PutMapping("/api/companies/{id}")
    public Map<String, Object> update(@PathVariable long id,
            @RequestBody Map<String, Object> body) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "companies:manage");
        one(id);
        var sets = new LinkedHashMap<String, Object>();
        for (String column : List.of("name", "contact_person", "email", "phone", "address",
                "is_active")) {
            if (body.containsKey(column)) {
                sets.put(column + " = ?", body.get(column));
            }
        }
        if (!sets.isEmpty()) {
            sets.put("updated_at = NOW()", null);
            jdbc.update("UPDATE companies SET " + String.join(", ", sets.keySet())
                    + " WHERE id = ?", sets.values().toArray());
        }
        audit.event(userId, "company_updated", "company", id, null);
        return one(id);
    }

    @DeleteMapping("/api/companies/{id}")
    public Map<String, Object> delete(@PathVariable long id) {
        long userId = CurrentUser.require().userId();
        gate.check(userId, "companies:manage");
        one(id);
        jdbc.update("UPDATE companies SET is_active = false WHERE id = ?", id);
        audit.event(userId, "company_deleted", "company", id, null);
        return message("Company deleted successfully");
    }

    private Map<String, Object> one(long id) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT * FROM companies WHERE id = ?", id);
        if (rows.isEmpty()) {
            throw ApiError.notFound("Company not found");
        }
        return rows.get(0);
    }
}
