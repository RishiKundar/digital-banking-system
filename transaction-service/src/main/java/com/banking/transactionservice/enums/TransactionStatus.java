package com.banking.transactionservice.enums;

/*
    Transaction Life Cycle
    PENDING -> PROCESSING -> COMPLETED (In case of clean txn)
                          -> PENDING_VERIFICATION (Suspicious detected)
                                           -> COMPLETED (if verified)
                                           -> FLAGGED (SAGA refund)
                          -> FAILED
                          -> FLAGGED
 */

public enum TransactionStatus {

    PENDING,
    PROCESSING,
    PENDING_VERIFICATION,
    COMPLETED,
    FAILED,
    FLAGGED
}
