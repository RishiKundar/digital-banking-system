package com.banking.accountservice.kafka;

import com.banking.accountservice.service.AccountService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
@Primary
public class AccountServiceConsumer {

    private final AccountService accountService;

    /*
    Consume transaction completed event from kafka
    mean: No fraud detected crediting to receiver
     */
    @KafkaListener(topics = "transaction.completed")
    public void consumeTransactionCompleted(@Payload Map<String,Object> payload)
    {
        try{
            String receiverAccount = payload.get("receiverAccountNumber").toString();
            BigDecimal amount = new BigDecimal(payload.get("amount").toString());
            log.info("Crediting account : {} amount : {}", receiverAccount, amount);
            accountService.creditBalance(receiverAccount,amount);
        } catch (Exception e) {
            log.error("Error crediting account: {}", e.getMessage());
        }
    }


    /*
    Consume fraud.detected event from kafka
    Blocks the flagged account
    mean: No fraud detected crediting to receiver
     */
    @KafkaListener(topics = "fraud.detection")
    public void consumeFraudDetected(@Payload Map<String,Object> payload){
        try{
            String accountNumber = payload.get("accountNumber").toString();
            log.info("Fraud Detected --> Blocking the account : {}", accountNumber);
            accountService.blockAccount(accountNumber);
        } catch (Exception e) {
            log.error("Error Blocking Account : {}", e.getMessage());
        }
    }
}
