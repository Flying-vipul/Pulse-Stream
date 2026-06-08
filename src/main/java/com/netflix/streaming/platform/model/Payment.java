package com.netflix.streaming.platform.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    // 🛡️ THE FIX: Replaced "Order" with "User"
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String paymentMethod;
    private String pgPaymentId;
    private String pgStatus;

    @Column(unique = true)
    private String razorpayOrderId;
    private String razorpaySignature;

    @CreationTimestamp
    private LocalDateTime createdAt;
}