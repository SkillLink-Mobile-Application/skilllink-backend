
package com.skilllink.controller;

import com.skilllink.service.PasswordResetService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(
            PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    // =====================================================
    // STEP 1: SEND OTP
    // =====================================================

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(
            @RequestBody Map<String, String> request) {

        System.out.println("========== FORGOT PASSWORD REQUEST ==========");

        String email = request.get("email");

        if (email == null || email.isBlank()) {
            System.out.println("Email is missing");

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email is required"));
        }

        System.out.println("Email received: " + email);

        try {
            passwordResetService.sendOtp(email);

            System.out.println("OTP REQUEST PROCESSED");

            return ResponseEntity.ok(
                    Map.of(
                            "message",
                            "If the account exists, an OTP will be sent to your email."
                    )
            );

        } catch (Exception e) {
            System.out.println("OTP SENDING ERROR: " + e.getMessage());

            return ResponseEntity.internalServerError()
                    .body(Map.of("message", "Unable to send OTP"));
        }
    }

    // =====================================================
    // STEP 2: VERIFY OTP
    // =====================================================

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<?> verifyOtp(
            @RequestBody Map<String, String> request) {

        System.out.println("========== OTP VERIFY REQUEST RECEIVED ==========");

        String email = request.get("email");
        String otp = request.get("otp");

        System.out.println("Email received: " + email);
        System.out.println("OTP received: " +
                (otp == null ? "NULL" : "6-digit value received"));

        if (email == null || email.isBlank() ||
                otp == null || otp.isBlank()) {

            System.out.println("Email or OTP is missing");

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Email and OTP are required"));
        }

        try {
            String resetToken =
                    passwordResetService.verifyOtp(email, otp);

            System.out.println("OTP VERIFIED SUCCESSFULLY");

            return ResponseEntity.ok(
                    Map.of(
                            "message", "OTP verified successfully",
                            "resetToken", resetToken
                    )
            );

        } catch (IllegalArgumentException e) {

            System.out.println(
                    "OTP VERIFICATION FAILED: " + e.getMessage());

            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {

            System.out.println(
                    "OTP SERVER ERROR: " + e.getMessage());

            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "message",
                            "Server error during OTP verification"
                    ));
        }
    }

    // =====================================================
    // STEP 3: RESET PASSWORD
    // =====================================================

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(
            @RequestBody Map<String, String> request) {

        System.out.println("========== RESET PASSWORD REQUEST ==========");

        String email = request.get("email");
        String resetToken = request.get("resetToken");
        String newPassword = request.get("newPassword");

        if (email == null || email.isBlank() ||
                resetToken == null || resetToken.isBlank() ||
                newPassword == null || newPassword.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "All fields are required"));
        }

        try {
            passwordResetService.resetPassword(
                    email, resetToken, newPassword);

            System.out.println("PASSWORD RESET SUCCESSFUL");

            return ResponseEntity.ok(
                    Map.of("message", "Password reset successfully")
            );

        } catch (IllegalArgumentException e) {

            System.out.println(
                    "PASSWORD RESET FAILED: " + e.getMessage());

            return ResponseEntity.badRequest()
                    .body(Map.of("message", e.getMessage()));

        } catch (Exception e) {

            System.out.println(
                    "PASSWORD RESET SERVER ERROR: " + e.getMessage());

            e.printStackTrace();

            return ResponseEntity.internalServerError()
                    .body(Map.of(
                            "message",
                            "Server error during password reset"
                    ));
        }
    }
}
