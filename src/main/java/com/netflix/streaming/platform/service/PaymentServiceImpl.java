package com.netflix.streaming.platform.service;

import com.netflix.streaming.platform.exceptions.APIException;
import com.netflix.streaming.platform.model.Payment;
import com.netflix.streaming.platform.model.PlanTier;
import com.netflix.streaming.platform.model.User;
import com.netflix.streaming.platform.payload.OrderResponseDTO;
import com.netflix.streaming.platform.payload.PaymentVerificationDTO;
import com.netflix.streaming.platform.repositories.PaymentRepository;
import com.netflix.streaming.platform.repositories.UserRepository;

import com.netflix.streaming.platform.security.AuthUtil;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional // 🛡️ If any step fails (like the DB goes down), the whole transaction rolls back!
public class PaymentServiceImpl implements PaymentService {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PaymentRepository paymentRepository;
    @Autowired
    private AuthUtil authUtil;
    @Autowired
    private EmailService emailService; // Injecting your custom Email Service

    @Value("${razorpay.key_id}")
    private String razorpayKeyId;

    @Value("${razorpay.key_secret}")
    private String razorpayKeySecret;

    // -------------------------------------------------------------
    // STEP 1: CREATE THE ORDER (NOW WITH PRORATED UPGRADES!)
    // -------------------------------------------------------------
    @Override
    public OrderResponseDTO createRazorpayOrder(String requestedTier) {
        try {
            // 1. Fetch the user making the request
            User loggedInUser = authUtil.loggedInUser();
            String currentTier = loggedInUser.getPlanTier().name();

            // 2. Calculate the prices
            int targetPrice = getPriceForPlan(requestedTier);
            int currentPrice = getPriceForPlan(currentTier);

            // 3. The Business Logic Guardrails
            if (currentTier.equalsIgnoreCase(requestedTier)) {
                throw new APIException("You are already on the " + requestedTier + " plan!");
            }

            if (currentPrice > targetPrice) {
                throw new APIException("Downgrades are not supported through this endpoint.");
            }

            // 4. THE MAGIC: Calculate the upgrade difference!
            // If Basic (199) -> Premium (799) = They only pay 600.
            // If they are a brand-new user (Assuming currentPrice is 0 for free accounts), they pay full.
            int finalAmountToCharge = targetPrice - currentPrice;

            // Convert to paise for Razorpay
            int amountInPaise = finalAmountToCharge * 100;

            // 5. Build the Razorpay Request
            RazorpayClient razorpay = new RazorpayClient(razorpayKeyId, razorpayKeySecret);
            JSONObject orderRequest = new JSONObject();
            orderRequest.put("amount", amountInPaise);
            orderRequest.put("currency", "INR");
            orderRequest.put("receipt", "txn_upgrade_" + System.currentTimeMillis());

            Order razorpayOrder = razorpay.orders.create(orderRequest);

            return new OrderResponseDTO(
                    razorpayOrder.get("id"),
                    finalAmountToCharge, // Tell the frontend the exact prorated amount!
                    "INR"
            );

        } catch (RazorpayException e) {
            throw new APIException("Error creating Razorpay Order: " + e.getMessage());
        }
    }




@Override
public boolean verifyPaymentSignature(PaymentVerificationDTO dto) {

    System.out.println("========== PAYMENT DATA RECEIVED ==========");
    System.out.println("Payment ID: " + dto.getRazorpayPaymentId());
    System.out.println("Order ID: " + dto.getRazorpayOrderId());
    System.out.println("Signature: " + dto.getRazorpaySignature());
    System.out.println("Tier: " + dto.getPlanTier());
    System.out.println("===========================================");
    try {
        // 1. Build the options EXACTLY how Razorpay's SDK expects them
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", dto.getRazorpayOrderId());
        options.put("razorpay_payment_id", dto.getRazorpayPaymentId());
        options.put("razorpay_signature", dto.getRazorpaySignature());

        // 2. Mathematically verify the signature
        boolean isValidSignature = Utils.verifyPaymentSignature(options, razorpayKeySecret);

        if (isValidSignature) {
            // 3. Fetch the logged-in user
            User loggedInUser = authUtil.loggedInUser();

            // 4. Double-charge protection
            if(paymentRepository.existsByRazorpayOrderId(dto.getRazorpayOrderId())) {
                throw new APIException("Payment already processed!");
            }

            // 5. Build and Save the Payment History
            Payment payment = new Payment();
            payment.setRazorpayOrderId(dto.getRazorpayOrderId());
            payment.setPgPaymentId(dto.getRazorpayPaymentId());
            payment.setRazorpaySignature(dto.getRazorpaySignature());
            payment.setPgStatus("SUCCESS");
            payment.setPaymentMethod("RAZORPAY");

            // 🛡️ THE CRITICAL FIX: Attach the User to prevent the 500 DB Crash!
            payment.setUser(loggedInUser);

            paymentRepository.save(payment);

            // 6. UPGRADE THE USER! (Using your flawless Enum logic)
            loggedInUser.setPlanTier(PlanTier.valueOf(dto.getPlanTier().toUpperCase()));
            userRepository.save(loggedInUser);

            // 7. FIRE THE EMOJI HYPE EMAIL
            String emailSubject = "🎬 Welcome to Pulse Stream " + dto.getPlanTier().toUpperCase() + "! 🍿";

            String emailBody = "Hey " + loggedInUser.getEmail() + " 👋,\n\n" +
                    "Boom! 💥 Your payment was an absolute success! 🚀\n\n" +
                    "Your account has been officially upgraded to the " + dto.getPlanTier().toUpperCase() + " tier. 🏆\n\n" +
                    "The ultimate cinematic universe is now fully unlocked. Whether you are rewatching Spider-Man, catching up on Loki, or prepping for Doomsday, your high-res streams are ready to go. 🕸️🦸‍♂️🎥\n\n" +
                    "Grab some mass gainer snacks 🍿, dim the lights 🛋️, and hit play!\n\n" +
                    "Happy Streaming! 🎬\n" +
                    "- The Pulse Stream Team ✨";

            emailService.sendSimpleMessage(loggedInUser.getEmail(), emailSubject, emailBody);

            return true;
        } else {
            return false; // Signature was fake/hacked
        }

    } catch (RazorpayException e) {
        throw new APIException("Signature Verification Failed: " + e.getMessage());
    }
}

    // --- HELPER METHOD ---
    private int getPriceForPlan(String planTier) {
        return switch (planTier.toUpperCase()) {
            case "NONE" -> 0;     // 🛡️ The magic fix!
            case "BASIC" -> 199;
            case "STANDARD" -> 499;
            case "PREMIUM" -> 799;
            default -> throw new APIException("Invalid Plan Tier Selected!");
        };
    }
}