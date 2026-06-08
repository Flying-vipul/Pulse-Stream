package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.payload.OrderResponseDTO;
import com.netflix.streaming.platform.payload.PaymentVerificationDTO;

public interface PaymentService {

    // 🛡️ CHANGED: Now strictly returns our DTO wrapper
    OrderResponseDTO createRazorpayOrder(String planTier);

    boolean verifyPaymentSignature(PaymentVerificationDTO verificationDTO);
}