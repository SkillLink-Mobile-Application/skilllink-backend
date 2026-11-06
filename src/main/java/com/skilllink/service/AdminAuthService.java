package com.skilllink.service;

import com.skilllink.dto.AdminDtos.LoginResponse;
import com.skilllink.entity.PasswordResetOtp;
import com.skilllink.entity.User;
import com.skilllink.entity.UserRole;
import com.skilllink.enums.Role;
import com.skilllink.repository.PasswordResetOtpRepository;
import com.skilllink.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;

@Service
public class AdminAuthService {

    private static final int OTP_VALID_MINUTES = 10;
    private static final int RESET_TOKEN_VALID_MINUTES = 10;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int RESEND_COOLDOWN_SECONDS = 60;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final PasswordResetOtpRepository otpRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AdminMailService mailService;

    public AdminAuthService(
            UserRepository userRepository,
            PasswordResetOtpRepository otpRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            AdminMailService mailService
    ) {
        this.userRepository = userRepository;
        this.otpRepository = otpRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.mailService = mailService;
    }

    // ================= HELPERS =================

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }

    private boolean isAdmin(User user) {
        return user.getRoles().stream().anyMatch(r -> r.getRole() == Role.ADMIN);
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be between 8 and 72 characters.");
        }
    }

    // ================= LOGIN =================

    @Transactional(readOnly = true)
    public LoginResponse login(String rawEmail, String password) {
        String email = normalize(rawEmail);

        User user = userRepository.findByEmail(email).orElse(null);

        if (user == null
                || password == null
                || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Invalid email or password");
        }

        if (!isAdmin(user)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "This account doesn't have administrator access.");
        }

        List<String> roles = user.getRoles().stream()
                .map(UserRole::getRole)
                .map(Enum::name)
                .toList();

        String token = jwtService.generateToken(user);

        String name = user.getName() == null || user.getName().isBlank()
                ? user.getEmail()
                : user.getName();

        return new LoginResponse(token, roles, name, user.isMustChangePassword());
    }

    // ================= FORGOT PASSWORD: STEP 1 (send OTP) =================

    // Always behaves the same from the caller's view, so it can't be used to
    // discover which emails belong to admins.
    @Transactional
    public void requestOtp(String rawEmail) {
        String email = normalize(rawEmail);

        if (email.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email is required.");
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null || !isAdmin(user)) {
            return;
        }

        PasswordResetOtp latest = otpRepository.findTopByEmailOrderByIdDesc(email).orElse(null);
        if (latest != null
                && latest.getCreatedAt().isAfter(LocalDateTime.now().minusSeconds(RESEND_COOLDOWN_SECONDS))) {
            return; // too soon; ignore quietly
        }

        otpRepository.deleteByEmail(email);

        String otp = String.format("%05d", RANDOM.nextInt(100000));
        LocalDateTime now = LocalDateTime.now();

        PasswordResetOtp record = new PasswordResetOtp();
        record.setEmail(email);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setCreatedAt(now);
        record.setExpiresAt(now.plusMinutes(OTP_VALID_MINUTES));
        otpRepository.save(record);

        // If sending fails this throws and the transaction rolls back.
        mailService.sendOtp(user.getEmail(), otp);
    }

    // ================= FORGOT PASSWORD: STEP 2 (check OTP) =================

    // noRollbackFor: the failed-attempt counter must still be saved when we throw.
    @Transactional(noRollbackFor = ResponseStatusException.class)
    public String verifyOtp(String rawEmail, String otp) {
        String email = normalize(rawEmail);

        PasswordResetOtp record = otpRepository.findTopByEmailOrderByIdDesc(email).orElse(null);

        if (record == null || record.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This code is invalid or has expired. Please request a new one.");
        }

        if (record.getAttempts() >= MAX_OTP_ATTEMPTS) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong attempts. Please request a new code.");
        }

        if (otp == null || !passwordEncoder.matches(otp.trim(), record.getOtpHash())) {
            record.setAttempts(record.getAttempts() + 1);
            otpRepository.save(record);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Incorrect code.");
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String resetToken = HexFormat.of().formatHex(bytes);

        record.setVerified(true);
        record.setResetTokenHash(passwordEncoder.encode(resetToken));
        record.setResetTokenExpiry(LocalDateTime.now().plusMinutes(RESET_TOKEN_VALID_MINUTES));
        otpRepository.save(record);

        return resetToken;
    }

    // ================= FORGOT PASSWORD: STEP 3 (new password) =================

    @Transactional
    public void resetPassword(String rawEmail, String resetToken, String newPassword) {
        String email = normalize(rawEmail);
        validatePassword(newPassword);

        String expired = "This reset session has expired. Please start again.";

        PasswordResetOtp record = otpRepository.findTopByEmailOrderByIdDesc(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, expired));

        if (!record.isVerified()
                || record.getResetTokenHash() == null
                || record.getResetTokenExpiry() == null
                || record.getResetTokenExpiry().isBefore(LocalDateTime.now())
                || resetToken == null
                || !passwordEncoder.matches(resetToken, record.getResetTokenHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, expired);
        }

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, expired));

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);

        otpRepository.deleteByEmail(email);
    }

    // ================= CHANGE PASSWORD (signed in) =================

    // Uses 400 (not 401) for a wrong current password so the frontend doesn't log the admin out.
    @Transactional
    public void changePassword(String email, String currentPassword, String newPassword) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (currentPassword == null
                || !passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Current password is incorrect.");
        }

        validatePassword(newPassword);

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "New password must be different from your current password.");
        }

        user.setPassword(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        userRepository.save(user);
    }
}