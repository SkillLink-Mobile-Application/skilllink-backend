package com.skilllink.controller;

import com.skilllink.dto.AdminDtos.*;
import com.skilllink.enums.ProviderApprovalStatus;
import com.skilllink.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

// Protected by SecurityConfig:
//   /api/admin/admins/**  -> ROLE_SUPER_ADMIN
//   /api/admin/**         -> ROLE_ADMIN
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    // Return errors as { "message": "..." } so the frontend can show them.
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Request failed" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    // ---------- Dashboard ----------

    @GetMapping("/stats")
    public StatsResponse stats() {
        return adminService.getStats();
    }

    // ---------- Users ----------

    @GetMapping("/users")
    public List<UserRow> users() {
        return adminService.listUsers();
    }

    @PutMapping("/users/{id}/block")
    public UserRow block(@PathVariable Long id) {
        return adminService.setBlocked(id, true);
    }

    @PutMapping("/users/{id}/unblock")
    public UserRow unblock(@PathVariable Long id) {
        return adminService.setBlocked(id, false);
    }

    // ---------- Admins (Super Admin only) ----------

    @GetMapping("/admins")
    public List<AdminRow> admins(Authentication authentication) {
        return adminService.listAdmins(authentication.getName());
    }

    @PostMapping("/admins")
    public ResponseEntity<AdminRow> createAdmin(
            @RequestBody CreateAdminRequest request,
            Authentication authentication
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(adminService.createAdmin(request, authentication.getName()));
    }

    @PutMapping("/admins/{id}")
    public AdminRow updateAdmin(
            @PathVariable Long id,
            @RequestBody UpdateAdminRequest request,
            Authentication authentication
    ) {
        return adminService.updateAdmin(id, request, authentication.getName());
    }

    @DeleteMapping("/admins/{id}")
    public ResponseEntity<Map<String, String>> deleteAdmin(
            @PathVariable Long id,
            Authentication authentication
    ) {
        adminService.deleteAdmin(id, authentication.getName());
        return ResponseEntity.ok(Map.of("message", "Admin deleted"));
    }

    // ---------- Provider applications ----------

    @GetMapping("/provider-applications")
    public List<ProviderApplicationRow> applications() {
        return adminService.listApplications();
    }

    @PutMapping("/provider-applications/{id}/approve")
    public ProviderApplicationRow approve(@PathVariable Long id) {
        return adminService.setApplicationStatus(id, ProviderApprovalStatus.APPROVED);
    }

    @PutMapping("/provider-applications/{id}/reject")
    public ProviderApplicationRow reject(@PathVariable Long id) {
        return adminService.setApplicationStatus(id, ProviderApprovalStatus.REJECTED);
    }
}