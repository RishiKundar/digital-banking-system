package com.banking.accountservice.utils;


import com.banking.accountservice.enums.AccountType;
import com.banking.accountservice.repo.AccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.security.SecureRandom;

@Component
@RequiredArgsConstructor
public class AccountUtils {

    @Autowired
    private SecureRandom secureRandom;

    private final AccountRepository accountRepository;




    // Generates 12 Digit Unique Account Number
    public String generateAccountNumber(){
        String accountNumber;
        do{
            long number = secureRandom.nextLong(1_000_000_000_000L);
            accountNumber = String.format("%012d",number);
        }while(accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }

    public BigDecimal getDailyTransactionLimit(AccountType accountType){
        if(accountType.equals(AccountType.SAVINGS)){
            return new BigDecimal("100000");
        }else if (accountType.equals(AccountType.CURRENT) || accountType.equals(AccountType.FIXED_DEPOSIT)){
            return new BigDecimal("50000");
        }
        throw new RuntimeException("Unknown Account Type");
    }
}
