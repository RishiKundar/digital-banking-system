package com.banking.notificationservice.service;


import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class NotificationService {


    @KafkaListener(
            topics = "transaction.otp.generated"
    )
    public void consumeOtpGenerated(
            @Payload Map<String,Object> paylaod
            ){
        try{
            String accountNumber = paylaod.get("accountNumber").toString();
            String otp = paylaod.get("otp").toString();
            String transactionId = paylaod.get("transactionId").toString();
            String reason = paylaod.get("reason").toString();
            String amount = paylaod.get("amount").toString();

            sendAlert(
                    "TRANSACTION VERIFICATION REQUIRED",
                    accountNumber,
                    String.format(
                            "Suspicious activity detected on your account. "
                            + "Reason %s "
                            + "A transaction of %s is pending verification. "
                            + "Your OTP is: %s. Valid for 5 Mins "
                            + "If this is you. Ignore the message ",
                            reason,amount,otp
                    )
            );
        } catch (Exception e) {
            log.error("Error in sending OTP notification:");
        }
    }


    @KafkaListener(
            topics = "transaction.completed"
    )
    public void consumeTransactionCompleted(@Payload Map<String,Object> payload){
        try{
            String senderAccount = payload.get("senderAccountNumber").toString();
            String receiverAccount = payload.get("receiverAccountNumber").toString();
            String amount = payload.get("amount").toString();

            // Debit Alert
            sendAlert(
                    "DEBIT ALERT",
                    senderAccount,
                    String.format("%s Debited from account %s",amount,senderAccount)
            );

            // Credit Alert
            sendAlert(
                    "CREDIT ALERT",
                    receiverAccount,
                    String.format("%s Credit from account %s",amount,senderAccount)
            );
        } catch (Exception e) {
            log.error("Error Sending Transaction Notification {} ", e.getMessage());
        }
    }


    @KafkaListener(
            topics = "fraud.detected"
    )
    public void consumeFraudDetected(@Payload Map<String,Object> payload){
        try{
            String accountNumber = payload.get("accountNumber").toString();
            String reason = payload.get("reason").toString();


            sendAlert(
                    "SUSPICIOUS ACTIVITY DETECTED",
                    accountNumber,
                    String.format(
                            "Your Account %s has been blocked"
                            + "Reason: %s"
                            + "Please contact Bank Immediately",
                            accountNumber,reason
                    )
            );
        }catch (Exception e){
            log.error("Error Sending Fraud Notification {} ", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "transaction.refunded"
    )
    public void consumeTransactionRefunded(@Payload Map<String,Object> payload){
        try{
            String senderAccount = payload.get("senderAccountNumber").toString();
            String amount = payload.get("amount").toString();
            String reason = payload.get("reason").toString();

            sendAlert(
                    "REFUND PROCEESSED",
                    senderAccount,
                    String.format(
                            "Your transaction of %s has cancelled"
                            + "Reason: %s "
                            + "%s has been refunded to your account %s ",
                            amount,reason,amount,senderAccount
                    )
            );
        } catch (Exception e) {
            log.error("Error Sending Refund Notification {} ", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "payment.completed"
    )
    public void consumePaymentCompleted(@Payload Map<String,Object> payload){
        try{
            String accountNumber = payload.get("accountNumber").toString();
            String amount = payload.get("amount").toString();
            String razorpayId = payload.get("razorpayPaymentId").toString();
            sendAlert(
                    "PAYMENT SUCCESSFUL",
                    accountNumber,
                    String.format(
                            "Payment of %s completed"
                            + "Razorpay Id : %s",
                            amount,razorpayId

                    )
            );
        } catch (Exception e) {
            log.error("Error Sending payment successful Notification {} ", e.getMessage());
        }
    }

    @KafkaListener(
            topics = "payment.failed"
    )
    public void consumePaymentFailed(@Payload Map<String,Object> payload){
        try{
            String accountNumber = payload.get("accountNumber").toString();
            String amount = payload.get("amount").toString();

            sendAlert(
                    "MY PAYMENT FAILED",
                    accountNumber,
                    String.format(
                            "Your payment of %s could not be processed "
                            + "Please try again or contact support",
                            amount
                    )
            );
        } catch (Exception e) {
            log.error("Error Sending payment failed Notification {} ", e.getMessage());
        }
    }

    private void sendAlert(String subject ,String accountNumber, String message) {

        log.info(" --------------  Subject {} Sender {} message {}  -------------- ", subject,accountNumber,message);
    }
}
