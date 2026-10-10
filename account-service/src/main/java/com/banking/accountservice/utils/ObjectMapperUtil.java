package com.banking.accountservice.utils;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.entity.Account;
import org.springframework.stereotype.Component;

@Component
public class ObjectMapperUtil {

    public static AccountResponse mapToResponse(Account account){
        AccountResponse accountResponse = new AccountResponse();
        accountResponse.setId(account.getId());
        accountResponse.setAccountNumber(account.getAccountNumber());
        accountResponse.setAccountHolderName(account.getAccountHolderName());
        accountResponse.setEmail(account.getEmail());
        accountResponse.setPhone(account.getPhone());
        accountResponse.setAccountStatus(account.getAccountStatus());
        accountResponse.setBalance(account.getBalance());
        accountResponse.setDailyTransactionLimit(account.getDailyTransactionLimit());
        accountResponse.setCreatedAt(account.getCreatedAt());
        return accountResponse;
    }

}
