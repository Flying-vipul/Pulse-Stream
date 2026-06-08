package com.netflix.streaming.platform.controller;

import com.netflix.streaming.platform.payload.MessageResponseDTO;
import com.netflix.streaming.platform.payload.OrderResponseDTO;
import com.netflix.streaming.platform.payload.PaymentRequestDTO;
import com.netflix.streaming.platform.payload.PaymentVerificationDTO;
import com.netflix.streaming.platform.repositories.UserRepository;
import com.netflix.streaming.platform.service.PaymentService;
import com.netflix.streaming.platform.service.UserDetailsImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.netflix.streaming.platform.model.User;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @Autowired
    private UserRepository userRepository;

    @PostMapping("/create-order")
    public ResponseEntity<OrderResponseDTO> createSubscriptionOrder(
            @RequestBody PaymentRequestDTO paymentRequest) {

        OrderResponseDTO orderResponse = paymentService.createRazorpayOrder(paymentRequest.getPlanTier());
        return ResponseEntity.ok(orderResponse);
    }

    @PostMapping("/verify")
    public ResponseEntity<MessageResponseDTO> verifyPaymentAndUpdateAccount(
            @RequestBody PaymentVerificationDTO verificationDTO,
            Authentication authentication) {

        // 🛡️ 1. GRAB THE JWT TOKEN TO IDENTIFY THE USER!



        boolean isSuccess = paymentService.verifyPaymentSignature(verificationDTO);

        if (isSuccess) {
            // 🛡️ 2. Find the exact user in the database
            UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
            Optional<User> optionalUser = userRepository.findByEmail(userDetails.getEmail());

            if (optionalUser.isPresent()) {
                User user = optionalUser.get();

                // 🛡️ 3. UPGRADE THE DATABASE RECORD!
                user.setPlanTier(com.netflix.streaming.platform.model.PlanTier.valueOf(verificationDTO.getPlanTier()));
                userRepository.save(user);

                return ResponseEntity.ok(new MessageResponseDTO("Payment successful! Account upgraded to " + verificationDTO.getPlanTier() + ".", true));
            } else {
                return ResponseEntity.badRequest().body(new MessageResponseDTO("Payment successful, but user not found.", false));
            }
        } else {
            return ResponseEntity.badRequest().body(new MessageResponseDTO("Payment verification failed. Invalid signature.", false));
        }
    }
}