package com.banking.transactionservice.kafka;

import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.enums.TransactionStatus;
import com.banking.transactionservice.repo.TransactionRepository;
import com.banking.transactionservice.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionEventConsumer {

    private final TransactionRepository transactionRepository;
    private final RedisTemplate<String,String> redisTemplate;
    private final TransactionService transactionService;
    private static final long OTP_EXPIRY_MINUTES = 5;
    private final KafkaTemplate<String,Object> kafkaTemplate;

    private static final String TRANSACTION_OTP_GENERATED_TOPIC = "transaction.otp.generated";


    /*
    Consumes verification.required
    Generates OTP and Ask user to verify
     */

    @KafkaListener(
            topics = "verification.required"
    )
    public void consumeVerificationRequired(@Payload Map<String,Object> payload){
        try{
            String transactionId = payload.get("transactionId").toString();
            String accountNumber = payload.get("accountNumber").toString();
            String reason = payload.get("reason").toString();

            log.info("Verification Required - transaction {} reason {}",transactionId,reason);

            Transaction transaction = transactionRepository.findById(UUID.fromString(transactionId))
                    .orElseThrow(() -> new RuntimeException("Transaction Not Found " + transactionId));

            if(transaction.getStatus() != TransactionStatus.PROCESSING){
                log.warn("Transaction {} not processing - skipping", transactionId);
            }
            // Generate OTP
            String otp = String.format("%06d", (int) (Math.random() * 900000) + 100000);

            // Store OTP in Redis
            String otpKey = "verification:otp" + transactionId;
            redisTemplate.opsForValue().set(otpKey,otp,OTP_EXPIRY_MINUTES, TimeUnit.MINUTES);

            transaction.setStatus(TransactionStatus.PENDING_VERIFICATION);
            transactionRepository.save(transaction);
            log.info("Otp Generated for Transaction {} expires in {} min", transactionId,OTP_EXPIRY_MINUTES);

            // Notify User
            Map<String,Object> otpEvent = new HashMap<>();
            otpEvent.put("transactionId",transactionId);
            otpEvent.put("accountNumber", accountNumber);
            otpEvent.put("reason", reason);
            otpEvent.put("otp", otp);
            otpEvent.put("amount", transaction.getAmount());

            kafkaTemplate.send(TRANSACTION_OTP_GENERATED_TOPIC,transactionId,otpEvent);

        } catch (Exception e) {
            log.error("Error handling verification required {} ", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "fraud.check.clean"
    )
    public void consumeFraudCheckCleanResult(
            @Payload Map<String,Object> payload
    ){
        try{
            String transactionId = payload.get("transactionId").toString();
            transactionService.processCleanResult(transactionId);
        } catch (Exception e) {
            log.error("Error in fraud check clean {}", e.getMessage());
        }
    }


}
