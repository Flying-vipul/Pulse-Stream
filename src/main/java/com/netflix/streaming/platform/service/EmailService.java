package com.netflix.streaming.platform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;


@Service
public class EmailService {

    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);

    @Autowired
    private JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;

    /**
     * @Async runs this in the background so the user doesn't wait.
     * @Retryable tells Spring: If this fails, wait 2 seconds (2000ms), and try again up to 3 times!
     */
    @Async
    @Retryable(
            retryFor = {Exception.class},
            maxAttempts = 3,
            backoff = @Backoff(delay = 2000)
    )
    public void sendOtpEmail(String toEmail, String otp) {
        logger.info("Attempting to send OTP email to: {}", toEmail);

        try {
            SimpleMailMessage message = new SimpleMailMessage();

            message.setFrom(senderEmail);
            message.setTo(toEmail);
            message.setSubject("Verify Your PulseStream Account");

            message.setText("Welcome to PulseStream! 🍿\n\n"
                    + "Your 6-digit verification code is: " + otp + "\n\n"
                    + "This code will expire in exactly 5 minutes.\n"
                    + "If you did not request this, please ignore this email.\n\n"
                    + "- The PulseStream Team");

            mailSender.send(message);
            logger.info("✅ SUCCESS: OTP email sent to: {}", toEmail);

        } catch (Exception e) {
            // This error will trigger the @Retryable mechanism automatically!
            logger.warn("⚠️ FAILED to send email to {}. Retrying... Error: {}", toEmail, e.getMessage());
            throw e;
        }
    }
}