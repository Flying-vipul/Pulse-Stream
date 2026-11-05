package com.netflix.streaming.platform.payload;

import lombok.Data;

@Data
public class PaymentVerificationDTO {
    private String razorpayPaymentId;
    private String razorpayOrderId;
    private String razorpaySignature;
    private String planTier;
}