package com.skilllink.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.UnsupportedEncodingException;

@Service
public class AdminMailService {

    private static final Logger log = LoggerFactory.getLogger(AdminMailService.class);

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    @Value("${app.mail.sender-name:SkillLink Team}")
    private String senderName;

    @Value("${app.admin.login-url:http://localhost:5173/login}")
    private String loginUrl;

    // DEVELOPMENT ONLY: when true, emails are printed to the console instead of sent.
    @Value("${app.mail.dev-log-only:false}")
    private boolean devLogOnly;

    public AdminMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendAdminCredentials(
            String to,
            String name,
            String roleLabel,
            String temporaryPassword
    ) {
        String body = """
                Hello %s,

                You have been added as a %s on the SkillLink Admin Portal.

                Your login details:
                  Email:              %s
                  Temporary password: %s

                Sign in here: %s

                For your security you will be asked to choose a new password
                the first time you sign in.

                If you were not expecting this email, please ignore it or contact SkillLink support.

                - SkillLink Team
                """.formatted(name, roleLabel, to, temporaryPassword, loginUrl);

        send(to, "Your SkillLink admin account", body);
    }

    public void sendOtp(String to, String otp) {
        String body = """
                Hello,

                Your SkillLink admin password reset code is:

                    %s

                This code expires in 10 minutes. Do not share it with anyone.
                If you did not request a password reset, you can ignore this email.

                - SkillLink Team
                """.formatted(otp);

        send(to, "Your SkillLink password reset code", body);
    }

    private void send(String to, String subject, String text) {
        if (devLogOnly) {
            log.warn("""

                    ================= DEV MAIL (NOT SENT) =================
                    To:      {}
                    Subject: {}
                    -------------------------------------------------------
                    {}
                    ========================================================
                    """, to, subject, text);
            return;
        }

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, false, "UTF-8");
            helper.setFrom(from, senderName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, false);
            mailSender.send(message);
        } catch (MessagingException | UnsupportedEncodingException | MailException e) {
            log.error("EMAIL SEND FAILED to {} | {}: {}",
                    to, e.getClass().getName(), e.getMessage(), e);

            throw new ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Couldn't send the email. Please try again in a moment."
            );
        }
    }
}