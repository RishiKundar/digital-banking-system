package com.banking.paymentservice.service;

import com.banking.paymentservice.dto.CreatePaymentRequest;
import com.banking.paymentservice.dto.PaymentOrderResponse;
import com.banking.paymentservice.entity.Payment;
import com.banking.paymentservice.enums.PaymentStatus;
import com.banking.paymentservice.repo.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class PaymentService {
    
    @Value("${razorpay.key-id}")
    private String keyId;
    @Value("${razorpay.key-secret}")
    private String keySecret;

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String PAYMENT_COMPLETED_TOPIC = "payment.completed";
    private static final String PAYMENT_FAILED_TOPIC = "payment.failed";

    /**
     * Create Razorpay Payment Order
     * Flow:
     * 1. Create order in razorpay
     * 2. save payment record in DB.
     * 3. return order dtls to frontend
     * 4. front end shows checkout page
     * 5. user pays
     * 6. razorpay calls webhook
     * @param createPaymentRequest
     * @return
     */

    public PaymentOrderResponse createOrder(@Valid CreatePaymentRequest createPaymentRequest) throws RazorpayException {
        log.info("Create Order in Razorpay account: {} amount: {} ", createPaymentRequest.getAccountNumber(), createPaymentRequest.getAmount());

        log.info("Razorpay Key Id : {} ", keyId);
        log.info("Razorpay Secret Key : {}", keySecret);
        RazorpayClient razorpayClient = new RazorpayClient(keyId,keySecret);
        
        // converted Amount
        int convertedAmount = createPaymentRequest.getAmount().multiply(BigDecimal.valueOf(100)).intValue();

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", convertedAmount);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt","rcpt_"+ System.currentTimeMillis() +UUID.randomUUID().toString().replace("-","").substring(0,10));

        Order order = razorpayClient.orders.create(orderRequest);

        log.info("Razorpay order created: {}",order.get("id").toString());

        // Save Payment Record
        Payment payment = Payment.builder()
                .razorpayOrderId(order.get("id").toString())
                .accountNumber(createPaymentRequest.getAccountNumber())
                .amount(createPaymentRequest.getAmount())
                .currency("INR")
                .status(PaymentStatus.CREATED)
                .description(createPaymentRequest.getDescription())
                .build();

        Payment savedPayment = paymentRepository.save(payment);
        return mapToResponse(savedPayment);
    }

    
    public void handleWebhook(Map<String, Object> payload) {
        log.info("Received razorpay webhook {} ", payload.get("event"));
        
        String event = payload.get("event").toString();
        
        if("payment.captured".equals(event)){
            handlePaymentSuccess(payload);
        }else if("payment.failed".equals(event)){
            handlePaymentFailure(payload);
        }
    }

    private void handlePaymentFailure(Map<String, Object> payload) {
        try{
            Map<String,Object> paymentData = extractPaymentData(payload);
            String orderId = paymentData.get("order_id").toString();
            String paymentId = paymentData.get("id").toString();

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not Found " + orderId));

            payment.setStatus(PaymentStatus.FAILED);
            payment.setFailureReason("Payment Failed Via Razorpay");
            paymentRepository.save(payment);

            Map<String,Object> event = new HashMap<>();
            event.put("paymentId", payment.getId().toString());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("reason", payment.getFailureReason());

            kafkaTemplate.send(PAYMENT_FAILED_TOPIC,payment.getId().toString(),event);
            log.info("Payment Failed {}", payment.getId().toString());

        } catch (Exception e) {
            log.error("Error Handling Payment Failed {}", e.getMessage());
        }
    }

    private Map<String, Object> extractPaymentData(Map<String, Object> payload) {
        Map<String,Object> entity = (Map<String,Object>) payload.get("payload");
        Map<String,Object> paymentWrapper = (Map<String, Object>) entity.get("payment");
        return (Map<String, Object>) paymentWrapper.get("entity");
    }

    private void handlePaymentSuccess(Map<String, Object> payload) {
        try{
            Map<String,Object> paymentData = extractPaymentData(payload);
            String orderId = paymentData.get("order_id").toString();
            String paymentId = paymentData.get("id").toString();

            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Order not Found " + orderId));

            payment.setRazorpayPaymentId(paymentId);
            payment.setStatus(PaymentStatus.COMPLETED);
            paymentRepository.save(payment);

            // Publish payment completed event to kafka
            Map<String,Object> event = new HashMap<>();
            event.put("paymentId", payment.getId().toString());
            event.put("accountNumber", payment.getAccountNumber());
            event.put("amount", payment.getAmount());
            event.put("razorpayPaymentId", paymentId);

            kafkaTemplate.send(PAYMENT_COMPLETED_TOPIC,payment.getId().toString(),event);
            log.info("Payment Completed {}", payment.getId().toString());
        } catch (Exception e) {
            log.error("Error Handling Payment Success {}", e.getMessage());
        }
    }


    private PaymentOrderResponse mapToResponse(Payment payment){
        return PaymentOrderResponse.builder()
                .paymentId(payment.getId())
                .accountNumber(payment.getAccountNumber())
                .currency(payment.getCurrency())
                .amount(payment.getAmount())
                .status(payment.getStatus())
                .razorpayOrderId(payment.getRazorpayOrderId())
                .razorpayKeyId(keyId)
                .build();

    }
}
