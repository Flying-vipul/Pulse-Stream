package com.netflix.streaming.platform.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public void sendOtpEmail(String toEmail, String otp) {
        logger.info("Attempting to send OTP email via Brevo to: {}", toEmail);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            // THE HACK: Real email address, Fake display name!
            helper.setFrom("noreply@zappit.online", "PulseStream");
            
            helper.setTo(toEmail);
            helper.setSubject("Your PulseStream Verification Code");
            
            // HTML for a beautiful UI
            String htmlContent = "<p>Welcome to PulseStream! 🍿</p>"
                               + "<p>Your 6-digit verification code is: <strong>" + otp + "</strong></p>"
                               + "<p>This code will expire in exactly 5 minutes.</p>"
                               + "<p>If you did not request this, please ignore this email.</p>"
                               + "<p>- The PulseStream Team</p>";
            
            helper.setText(htmlContent, true);

            mailSender.send(message);
            logger.info(" SUCCESS: OTP email sent via Brevo to: {}", toEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.warn(" FAILED to send email to {}. Retrying... Error: {}", toEmail, e.getMessage());
            throw new RuntimeException("Brevo SMTP failed to send email", e);
        }
    }

    /**
     * General Purpose Email Sender (For Payment Receipts!)
     */
    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public void sendSimpleMessage(String toEmail, String subject, String body) {
        logger.info("Attempting to send standard email via Brevo to: {}", toEmail);

        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setFrom("noreply@zappit.online", "PulseStream");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            
            // Replacing standard newlines with HTML breaks so receipts look good
            helper.setText("<p>" + body.replace("\n", "<br>") + "</p>", true);

            mailSender.send(message);
            logger.info(" SUCCESS: Standard email sent via Brevo to: {}", toEmail);

        } catch (MessagingException | UnsupportedEncodingException e) {
            logger.warn(" FAILED to send standard email to {}. Retrying... Error: {}", toEmail, e.getMessage());
            throw new RuntimeException("Brevo SMTP failed to send email", e);
        }
    }
}