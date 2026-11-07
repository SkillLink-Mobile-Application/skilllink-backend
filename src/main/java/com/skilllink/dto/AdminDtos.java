package com.skilllink.dto;

import java.util.List;

public final class AdminDtos {

    private AdminDtos() {
    }

    // ---------- Dashboard / lists ----------

    public record StatsResponse(
            long totalUsers,
            long serviceProviders,
            long pendingApplications,
            long totalAdmins
    ) {
    }

    public record UserRow(
            Long id,
            String name,
            String email,
            List<String> roles,
            String status
    ) {
    }

    // role = display label ("Super Admin" / "Admin"), roleCode = "SUPER_ADMIN" / "ADMIN"
    public record AdminRow(
            Long id,
            String name,
            String email,
            String role,
            String roleCode
    ) {
    }

    public record ProviderApplicationRow(
            Long id,
            String name,
            String email,
            String phone,
            String category,
            String nicNumber,
            String location,
            Integer experience,
            String description,
            String certificateFile,
            String status
    ) {
    }

    // ---------- Admin management ----------

    public record CreateAdminRequest(String name, String email, String role) {
    }

    public record UpdateAdminRequest(String name, String email, String role) {
    }

    // ---------- Admin authentication ----------

    public record LoginRequest(String email, String password) {
    }

    public record LoginResponse(
            String token,
            List<String> roles,
            String name,
            boolean mustChangePassword
    ) {
    }

    public record ForgotPasswordRequest(String email) {
    }

    public record VerifyOtpRequest(String email, String otp) {
    }

    public record VerifyOtpResponse(String resetToken) {
    }

    public record ResetPasswordRequest(String email, String resetToken, String newPassword) {
    }

    public record ChangePasswordRequest(String currentPassword, String newPassword) {
    }
}