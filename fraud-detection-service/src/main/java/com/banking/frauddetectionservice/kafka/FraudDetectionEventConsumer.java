package com.banking.frauddetectionservice.kafka;

import com.banking.frauddetectionservice.service.FraudDetectionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class FraudDetectionEventConsumer {

    private final FraudDetectionService fraudDetectionService;

    /*
    Listens to transaction initiated
    goes through event transaction to check fraud

     */
    @KafkaListener(
            topics = "transaction.initiated",
            groupId = "fraud-detection-group"
    )
    public void consumeTransactionInitiated(
            @Payload Map<String,Object> payload
    ){
        log.info("Received transaction for Fraud Detection");

        try{
            fraudDetectionService.checkTransaction(payload);
        } catch (Exception e) {

        }
    }
}
