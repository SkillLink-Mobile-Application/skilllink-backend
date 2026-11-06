package com.skilllink.controller;

import com.skilllink.dto.AdminDtos.*;
import com.skilllink.service.AdminAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/admin-auth")
public class AdminAuthController {

    private final AdminAuthService authService;

    public AdminAuthController(AdminAuthService authService) {
        this.authService = authService;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleStatus(ResponseStatusException ex) {
        String message = ex.getReason() == null ? "Request failed" : ex.getReason();
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("message", message));
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    @PostMapping("/forgot-password")
    public Map<String, String> forgotPassword(@RequestBody ForgotPasswordRequest request) {
        authService.requestOtp(request.email());
        return Map.of("message",
                "If this email belongs to an administrator, a 5-digit code has been sent.");
    }

    @PostMapping("/verify-otp")
    public VerifyOtpResponse verifyOtp(@RequestBody VerifyOtpRequest request) {
        return new VerifyOtpResponse(authService.verifyOtp(request.email(), request.otp()));
    }

    @PostMapping("/reset-password")
    public Map<String, String> resetPassword(@RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request.email(), request.resetToken(), request.newPassword());
        return Map.of("message", "Password updated. You can now sign in.");
    }

    @PutMapping("/change-password")
    public Map<String, String> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {
        authService.changePassword(
                authentication.getName(),
                request.currentPassword(),
                request.newPassword()
        );
        return Map.of("message", "Password changed successfully.");
    }
}