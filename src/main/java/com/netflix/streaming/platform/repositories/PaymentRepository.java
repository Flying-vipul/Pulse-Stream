package com.netflix.streaming.platform.repositories;

import com.netflix.streaming.platform.model.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // 🛡️ This is the exact method PaymentServiceImpl uses to prevent duplicate charges
    boolean existsByRazorpayOrderId(String razorpayOrderId);

}