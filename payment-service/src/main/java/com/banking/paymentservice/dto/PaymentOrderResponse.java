package com.banking.paymentservice.dto;

import com.banking.paymentservice.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentOrderResponse {
    private UUID paymentId;
    private String razorpayOrderId;
    private String accountNumber;
    private BigDecimal amount;
    private String currency;
    private PaymentStatus status;
    private String razorpayKeyId;

}
