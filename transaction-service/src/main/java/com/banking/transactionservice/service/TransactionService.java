package com.banking.transactionservice.service;

import com.banking.transactionservice.client.AccountServiceClient;
import com.banking.transactionservice.dto.TransactionResponse;
import com.banking.transactionservice.dto.TransferRequest;
import com.banking.transactionservice.entity.Transaction;
import com.banking.transactionservice.enums.TransactionStatus;
import com.banking.transactionservice.enums.TransactionType;
import com.banking.transactionservice.events.TransactionCompletedEvent;
import com.banking.transactionservice.events.TransactionInitiatedEvent;
import com.banking.transactionservice.repo.TransactionRepository;
import com.banking.transactionservice.utils.TransactionObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountServiceClient accountServiceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;
    private final RedisTemplate<String,String> redisTemplate;
    
    private static final String TRANSACTION_INIT_TOPIC = "transaction.initiated";
    private static final String TRANSACTION_COMPLETED_TOPIC = "transaction.completed";
    private static final String TRANSACTION_REFUNDED_TOPIC = "transaction.refunded";
    private static final String FRAUD_DETECTED_TOPIC = "fraud.detected";

    /*
    SAGA Step 1: Initiate Transfer
    Deduct from sender via Feign Client
    Save transaction as PROCESSING
    publish event to kafka for fraud check
     */
    public TransactionResponse transfer(TransferRequest request) {
        log.info("SAGA Start - Transfer {} -> {} amount {}", request.getSenderAccountNumber(), request.getReceiverAccountNumber(), request.getAmount());
        accountServiceClient.deductBalance(request.getSenderAccountNumber(), request.getAmount());

        Transaction transaction = Transaction.builder()
                .senderAccountNumber(request.getSenderAccountNumber())
                .receiverAccountNumber(request.getReceiverAccountNumber())
                .amount(request.getAmount())
                .type(TransactionType.TRANSFER)
                .status(TransactionStatus.PROCESSING)
                .description(request.getDescription())
                .referenceNumber(UUID.randomUUID().toString())
                .build();

        Transaction savedTransaction = transactionRepository.save(transaction);
        log.info("Transaction saved as PROCESSING :{}", savedTransaction.getId());

        // SAGA STEP 2: Publish for Fraud Check
        TransactionInitiatedEvent event = new TransactionInitiatedEvent();
        event.setTransactionId(savedTransaction.getId().toString());
        event.setSenderAccountNumber(savedTransaction.getSenderAccountNumber());
        event.setReceiverAccountNumber(savedTransaction.getReceiverAccountNumber());
        event.setAmount(savedTransaction.getAmount());

        kafkaTemplate.send(TRANSACTION_INIT_TOPIC,savedTransaction.getId().toString(),event);
        log.info("SAGA STEP 2 --> Transaction Initiated Event published :{} ", savedTransaction.getId().toString());

        return TransactionObjectMapper.mapToResponse(savedTransaction);
    }

    public TransactionResponse getTransaction(UUID transactionId) {
        Transaction transaction = transactionRepository.findById(transactionId).orElseThrow(() -> new RuntimeException("Transaction Not Found" + transactionId));
        return TransactionObjectMapper.mapToResponse(transaction);

    }

    public List<TransactionResponse> getTransactionHistory(String accountNumber) {
        List<Transaction> transactions = transactionRepository.findBySenderAccountNumberOrderByCreatedAtDesc(accountNumber);
        return transactions.stream().map(TransactionObjectMapper::mapToResponse).collect(Collectors.toList());
    }

    public TransactionResponse verifyOtp(UUID transactionId, String otp) {
        log.info("OTP Verification for the transaction: {} ", transactionId);

        Transaction transaction = transactionRepository.findById(transactionId)
                .orElseThrow(() -> new RuntimeException("Transaction not found " + transactionId.toString()));

        String otpKey = "verification:otp" + transactionId;
        String storedOtp = redisTemplate.opsForValue().get(otpKey);

        if(storedOtp == null){
            // OTP Expired
            log.warn("OTP expired for transaction: {}", transactionId);
            compensateTransaction(transaction, "OTP Expired - transaction cancelled and amount refunded");
            return TransactionObjectMapper.mapToResponse(transaction);
        }

        if(!storedOtp.equals(otp)){
            // BLOCK ACCOUNT AND REFUND
            log.warn("Wrong OTP - Blocking account and refunding : {}", transactionId);
            redisTemplate.delete(otpKey);
            blockAccountAndCompensate(transaction,"Wrong OTP entered - transaction cancelled, account blocked for security");
            return TransactionObjectMapper.mapToResponse(transaction);
        }

        log.info("OTP Verified - Completed Transaction: {}", transactionId);
        redisTemplate.delete(otpKey);
        completeTransaction(transaction);
        return TransactionObjectMapper.mapToResponse(transaction);
    }

    private void completeTransaction(Transaction transaction) {
        transaction.setStatus(TransactionStatus.COMPLETED);
        transaction.setCompletedAt(LocalDateTime.now());
        transactionRepository.save(transaction);

        TransactionCompletedEvent completedEvent = new TransactionCompletedEvent(
                transaction.getId().toString(),
                transaction.getSenderAccountNumber(),
                transaction.getReceiverAccountNumber(),
                transaction.getAmount(),
                transaction.getDescription()
        );

        kafkaTemplate.send(TRANSACTION_COMPLETED_TOPIC, transaction.getId().toString(),completedEvent);
        log.info("SAGA Complete - Transaction {} completed", transaction.getId());


    }

    private void blockAccountAndCompensate(Transaction transaction, String reason) {
        // Publish fruad.detected event --> Account service will block account;

        Map<String, Object> fraudEvent = new HashMap<>();
        fraudEvent.put("transactionId", transaction.getId());
        fraudEvent.put("accountNumber", transaction.getSenderAccountNumber());
        fraudEvent.put("reason", transaction.getFailureReason());

        kafkaTemplate.send(FRAUD_DETECTED_TOPIC, transaction.getSenderAccountNumber(),fraudEvent);
        log.warn("Fraud detected : amount {} will be refunded. Kindly contact to the bank", transaction.getAmount());
        compensateTransaction(transaction,reason);

    }

    private void compensateTransaction(Transaction transaction, String reason) {
        log.warn("SAGA Compensation - refunding: {} amount {}", transaction.getSenderAccountNumber(), transaction.getAmount());
        // CREDIT MONEY TO SENDER
        accountServiceClient.creditBalance(transaction.getSenderAccountNumber(),transaction.getAmount());

        transaction.setStatus(TransactionStatus.FLAGGED);
        transaction.setFailureReason(reason + "SAGA Compensation Executed and Amount refunded at " + LocalDateTime.now());
        transactionRepository.save(transaction);

        // Publish refund event - Notification service will alert user
        Map<String,Object> refundEvent = new HashMap<>();
        refundEvent.put("transactionId", transaction.getId());
        refundEvent.put("senderAccountNumber", transaction.getSenderAccountNumber());
        refundEvent.put("amount", transaction.getAmount());
        refundEvent.put("reason", transaction.getFailureReason());

        kafkaTemplate.send(TRANSACTION_REFUNDED_TOPIC,transaction.getId().toString(), refundEvent);

        log.info("SAGA COMPENSATION COMPLETED - {} refunded to {} ", transaction.getAmount(), transaction.getSenderAccountNumber());

    }

    public void processCleanResult(String transactionId) {
        Transaction transaction = transactionRepository.findById(UUID.fromString(transactionId))
                .orElseThrow(() -> new RuntimeException("Transaction not found " + transactionId));

        if(transaction.getStatus() != TransactionStatus.PROCESSING){
            log.warn("Transaction {} not PROCESSING - skiping", transactionId);
            return;
        }

        completeTransaction(transaction);

    }
}
