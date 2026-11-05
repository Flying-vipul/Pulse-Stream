// package com.netflix.streaming.platform.service;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.util.UriComponentsBuilder;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//@Service
//public class pythonEmailService {
//
//    private static final Logger logger = LoggerFactory.getLogger(EmailService.class);
//
//    // URL of your running Python Flask app
//    private final String PYTHON_SERVICE_URL = "https://python-email-service.vercel.app/api/send-email";
//
//    public void sendEmail(String to, String subject, String message) {
//        RestTemplate restTemplate = new RestTemplate();
//
//        // UriComponentsBuilder automatically encodes spaces and special characters
//        String url = UriComponentsBuilder.fromHttpUrl(PYTHON_SERVICE_URL)
//                .queryParam("to", to)
//                .queryParam("subject", subject)
//                .queryParam("message", message)
//                .toUriString();
//
//        try {
//            logger.info("Attempting to send email via Python Bridge to: {}", to);
//
//            // Execute the GET request
//            String response = restTemplate.getForObject(url, String.class);
//
//            logger.info(" SUCCESS: Python Bridge responded with: {}", response);
//        } catch (Exception e) {
//            logger.error(" FAILED: Could not reach Python Bridge. Error: {}", e.getMessage());
//        }
//    }
//}