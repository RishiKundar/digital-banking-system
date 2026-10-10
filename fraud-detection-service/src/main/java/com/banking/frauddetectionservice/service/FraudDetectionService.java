package com.banking.frauddetectionservice.service;

import com.banking.frauddetectionservice.client.AccountServiceClient;
import com.banking.frauddetectionservice.model.FraudCheckResults;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionService {

    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;

    @Value("${fraud.max-transactions-per-min}")
    private int maxTransactionsPerMin;

    @Value("${fraud.suspicious-amount-multiplier}")
    private Double suspiciousAmountMultiplier;

    @Value("${fraud.max-balance-percentage}")
    private Double maxBalancePercentage;

    private static final String VERIFICATION_REQUIRED_TOPIC = "verification.required";
    private static final String FRAUD_CHECK_CLEAN_RESULT_TOPIC = "fraud.check.clean";

    public void checkTransaction(Map<String,Object> payload){
        String transactionId = payload.get("transactionId").toString();
        String accountNumber = payload.get("senderAccountNumber").toString();
        BigDecimal amount = new BigDecimal(payload.get("amount").toString());

        // Fetch Balance
        BigDecimal balance = accountServiceClient.fetchBalance(accountNumber);
        log.info("Checking Transaction : {} account {} balance {}", transactionId,accountNumber,amount);

        // Perform Fraud Checks
        FraudCheckResults fraudCheckResults = performFraudCheck(accountNumber,amount,balance);

        if(fraudCheckResults.isFraud()){
            log.info("Suspicious Activity Detected - account {} Reason {} -> Requesting OTP Verification", accountNumber, fraudCheckResults.getReason());

            Map<String,Object> verificationEvent = new HashMap<>();
            verificationEvent.put("transactionId", transactionId);
            verificationEvent.put("accountNumber",accountNumber);
            verificationEvent.put("amount", amount);
            verificationEvent.put("reason", fraudCheckResults.getReason());

            kafkaTemplate.send(VERIFICATION_REQUIRED_TOPIC,transactionId,verificationEvent);
        }else{
            // Transaction is Clean
            log.info("Transaction Clean");
            Map<String,Object> transactionCleanEvent = new HashMap<>();
            transactionCleanEvent.put("transactionId", transactionId);
            transactionCleanEvent.put("isFraud",false);
            transactionCleanEvent.put("reason", null);

            kafkaTemplate.send(FRAUD_CHECK_CLEAN_RESULT_TOPIC,transactionId,transactionCleanEvent);
        }
    }

    private FraudCheckResults performFraudCheck(String accountNumber, BigDecimal amount, BigDecimal balance) {

        // Pattern 1: Velocity Check
        if(isVelocityExceeded(accountNumber)){
            return new FraudCheckResults(
                    true, "Two many transactions in 60 seconds - Velocity Limit Exceeded"
            );
        }

        // Pattern 2: Amount Check
        if(isAmountSuspicious(accountNumber, amount)){
            return new FraudCheckResults(
                    true, "Unusual transaction amount - Exceeds 3 times your average"
            );
        }


        // Pattern 3: Balance Check
        if(balance.compareTo(BigDecimal.ZERO) > 0 && isBalanceCheckFailed(balance, amount)){
            return new FraudCheckResults(
                    true, "Transaction exceeds 90% account balance"
            );
        }
        return new FraudCheckResults(false,null);
    }

    private boolean isBalanceCheckFailed(BigDecimal balance, BigDecimal amount) {
        BigDecimal maxAllowed = balance.multiply(BigDecimal.valueOf(maxBalancePercentage));
        log.info("Balance Check - amount {} maxAllowed {} suspicious {}", amount,maxAllowed, amount.compareTo(maxAllowed) > 0);
        return amount.compareTo(maxAllowed) > 0;
    }

    private boolean isAmountSuspicious(String accountNumber, BigDecimal amount) {
        String avgKey = "fraud:avg_amount" + accountNumber;
        String avgStr = redisTemplate.opsForValue().get(avgKey);

        if(avgStr == null){
            redisTemplate.opsForValue().set(avgKey,amount.toString());
            return false;
        }

        BigDecimal avgAmount = new BigDecimal(avgStr);
        BigDecimal threshold = avgAmount.multiply(BigDecimal.valueOf(suspiciousAmountMultiplier));

        //update running avg
        BigDecimal newAvg = avgAmount.add(amount).divide(BigDecimal.valueOf(2),2, RoundingMode.HALF_UP);

        redisTemplate.opsForValue().set(avgKey, newAvg.toString());

        log.info("Amount check - amount {} threshold {} suspicious {}",amount,threshold,amount.compareTo(threshold) > 0);
        return amount.compareTo(threshold) > 0;
    }

    private boolean isVelocityExceeded(String accountNumber) {
        String key = "fraud:velocity" + accountNumber;
        Long count = redisTemplate.opsForValue().increment(key);
        if(count != null && count == 1){
            redisTemplate.expire(key, 60, TimeUnit.SECONDS);
        }
        log.info("velocity check - account {} count {}/{}", accountNumber,count, maxTransactionsPerMin);
        return count != null && count > maxTransactionsPerMin;
    }
}
