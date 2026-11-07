
package com.skilllink.service;

import com.skilllink.entity.PasswordResetToken;
import com.skilllink.entity.User;
import com.skilllink.repository.PasswordResetTokenRepository;
import com.skilllink.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Optional;

@Service
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository resetRepository;
    private final JavaMailSender mailSender;
    private final PasswordEncoder passwordEncoder;

    private final SecureRandom random = new SecureRandom();

    @Value("${app.mail.from}")
    private String senderEmail;

    public PasswordResetService(
            UserRepository userRepository,
            PasswordResetTokenRepository resetRepository,
            JavaMailSender mailSender,
            PasswordEncoder passwordEncoder) {

        this.userRepository = userRepository;
        this.resetRepository = resetRepository;
        this.mailSender = mailSender;
        this.passwordEncoder = passwordEncoder;
    }

    // ============================================================
    // STEP 1: SEND OTP
    // ============================================================

    @Transactional
    public void sendOtp(String email) {

        if (email == null || email.isBlank()) {
            return;
        }

        String normalizedEmail = email.trim().toLowerCase();

        Optional<User> userOptional =
                userRepository.findByEmail(normalizedEmail);

        // Do not reveal whether an account exists.
        if (userOptional.isEmpty()) {
            return;
        }

        User user = userOptional.get();

        if (!user.isEmailVerified()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();

        PasswordResetToken record = resetRepository
                .findByEmail(normalizedEmail)
                .orElseGet(PasswordResetToken::new);

        // 60-second resend cooldown.
        if (record.getLastSentAt() != null &&
                record.getLastSentAt().plusSeconds(60).isAfter(now)) {
            return;
        }

        // Generate a six-digit OTP.
        int otpNumber = random.nextInt(900000) + 100000;
        String otp = String.valueOf(otpNumber);

        // Save hashed OTP.
        record.setEmail(normalizedEmail);
        record.setOtpHash(passwordEncoder.encode(otp));
        record.setOtpExpiry(now.plusMinutes(5));
        record.setLastSentAt(now);
        record.setAttempts(0);

        // Invalidate previous reset session.
        record.setResetTokenHash(null);
        record.setResetTokenExpiry(null);

        resetRepository.saveAndFlush(record);

        // Send email.
        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(normalizedEmail);
        message.setSubject("SkillLink Password Reset OTP");

        message.setText(
                "Hello " + user.getName() + ",\n\n" +
                        "Your SkillLink password reset OTP is:\n\n" +
                        otp + "\n\n" +
                        "This OTP will expire in 5 minutes.\n\n" +
                        "If you did not request this, please ignore this email.\n\n" +
                        "Thank you,\nSkillLink Team"
        );

        mailSender.send(message);
    }

    // ============================================================
    // STEP 2: VERIFY OTP
    // ============================================================

    @Transactional(noRollbackFor = IllegalArgumentException.class)
    public String verifyOtp(String email, String otp) {

        if (email == null || email.isBlank() ||
                otp == null || otp.isBlank()) {
            throw new IllegalArgumentException(
                    "Email and OTP are required");
        }

        String normalizedEmail = email.trim().toLowerCase();
        String cleanOtp = otp.trim();

        PasswordResetToken record = resetRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid or expired OTP"));

        LocalDateTime now = LocalDateTime.now();

        // Check OTP availability.
        if (record.getOtpHash() == null ||
                record.getOtpExpiry() == null) {

            throw new IllegalArgumentException(
                    "OTP is invalid or already used. Request a new OTP.");
        }

        // Check expiration.
        if (!record.getOtpExpiry().isAfter(now)) {

            throw new IllegalArgumentException(
                    "OTP has expired. Request a new OTP.");
        }

        // Check attempts.
        if (record.getAttempts() == null ||
                record.getAttempts() >= 5) {

            throw new IllegalArgumentException(
                    "Too many attempts. Request a new OTP.");
        }

        // Check OTP format.
        if (!cleanOtp.matches("\\d{6}")) {

            record.setAttempts(record.getAttempts() + 1);
            resetRepository.saveAndFlush(record);

            throw new IllegalArgumentException(
                    "Invalid OTP");
        }

        // Compare entered OTP with BCrypt hash.
        if (!passwordEncoder.matches(
                cleanOtp,
                record.getOtpHash())) {

            record.setAttempts(record.getAttempts() + 1);
            resetRepository.saveAndFlush(record);

            throw new IllegalArgumentException(
                    "Invalid OTP");
        }

        // OTP verified successfully.
        byte[] tokenBytes = new byte[32];
        random.nextBytes(tokenBytes);

        String resetToken =
                HexFormat.of().formatHex(tokenBytes);

        // Save hashed reset token.
        record.setResetTokenHash(
                passwordEncoder.encode(resetToken));

        record.setResetTokenExpiry(
                now.plusMinutes(10));

        // OTP can only be used once.
        record.setOtpHash(null);
        record.setOtpExpiry(null);

        resetRepository.saveAndFlush(record);

        return resetToken;
    }

    // ============================================================
    // STEP 3: RESET PASSWORD
    // ============================================================

    @Transactional
    public void resetPassword(
            String email,
            String resetToken,
            String newPassword) {

        if (email == null || email.isBlank() ||
                resetToken == null || resetToken.isBlank()) {

            throw new IllegalArgumentException(
                    "Invalid reset request");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (newPassword == null || newPassword.length() < 6) {

            throw new IllegalArgumentException(
                    "Password must contain at least 6 characters");
        }

        PasswordResetToken record = resetRepository
                .findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid reset request"));

        // Validate reset token.
        if (record.getResetTokenHash() == null ||
                record.getResetTokenExpiry() == null ||
                !record.getResetTokenExpiry()
                        .isAfter(LocalDateTime.now()) ||
                !passwordEncoder.matches(
                        resetToken,
                        record.getResetTokenHash())) {

            throw new IllegalArgumentException(
                    "Invalid or expired password reset session");
        }

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "Invalid reset request"));

        // Update password.
        user.setPassword(
                passwordEncoder.encode(newPassword));

        userRepository.save(user);

        // Delete reset record after successful password reset.
        resetRepository.delete(record);
    }
}
