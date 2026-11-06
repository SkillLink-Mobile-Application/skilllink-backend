package com.skilllink.service;

import com.skilllink.entity.User;
import com.skilllink.repository.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class EmailVerificationService {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;

    @Value("${app.mail.from}")
    private String senderEmail;

    @Value("${app.verification.base-url:http://localhost:8080}")
    private String verificationBaseUrl;

    public EmailVerificationService(
            JavaMailSender mailSender,
            UserRepository userRepository
    ) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
    }

    public void sendVerificationEmail(User user) {

        String token = UUID.randomUUID().toString();

        user.setEmailVerified(false);
        user.setEmailVerificationToken(token);
        user.setEmailVerificationTokenExpiry(
                LocalDateTime.now().plusHours(24)
        );

        userRepository.save(user);

        String baseUrl = verificationBaseUrl.replaceAll("/+$", "");

        String verificationLink = baseUrl
                + "/api/users/verify-email?token="
                + token;

        SimpleMailMessage message = new SimpleMailMessage();

        message.setFrom(senderEmail);
        message.setTo(user.getEmail());
        message.setSubject("Verify Your SkillLink Email");

        message.setText(
                "Hello " + user.getName() + ",\n\n"
                        + "Thank you for registering with SkillLink!\n\n"
                        + "Please click the link below to verify your email:\n\n"
                        + verificationLink + "\n\n"
                        + "This link will expire in 24 hours.\n\n"
                        + "If you did not create this account, please ignore this email.\n\n"
                        + "Thank you,\n"
                        + "SkillLink Team"
        );

        mailSender.send(message);
    }
}