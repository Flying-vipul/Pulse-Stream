package com.netflix.streaming.platform.payload;

import lombok.Data;

@Data
public class PaymentVerificationDTO {
    // 🚨 NO MORE ANNOTATIONS. JUST CLEAN VARIABLES.
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;
    private String planTier;
}