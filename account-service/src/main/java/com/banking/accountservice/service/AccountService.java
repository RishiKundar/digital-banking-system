package com.banking.accountservice.service;

import com.banking.accountservice.dto.AccountResponse;
import com.banking.accountservice.dto.CreateAccountRequest;
import com.banking.accountservice.entity.Account;
import com.banking.accountservice.enums.AccountStatus;
import com.banking.accountservice.repo.AccountRepository;
import com.banking.accountservice.utils.AccountUtils;
import com.banking.accountservice.utils.ObjectMapperUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;


@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountUtils accountUtils;

    public AccountResponse createAccount(CreateAccountRequest request) {
        log.info("Creating account for : {} ", request.getEmail());
        if(accountRepository.existsByEmail(request.getEmail())){
            throw new RuntimeException("Account Already Exists for Email: " + request.getEmail());
        }

        Account account = Account.builder()
                .accountHolderName(request.getAccountHolderName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .accountType(request.getAccountType())
                .accountStatus(AccountStatus.ACTIVE)
                .balance(request.getInitialDeposit())
                .accountNumber(accountUtils.generateAccountNumber())
                .dailyTransactionLimit(accountUtils.getDailyTransactionLimit(request.getAccountType()))
                .build();

        Account savedAccount = accountRepository.save(account);
        log.info("Account Created: {} ", savedAccount.getAccountNumber());

        return ObjectMapperUtil.mapToResponse(savedAccount);
    }

    public AccountResponse getAccount(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account Not Found"));
        return ObjectMapperUtil.mapToResponse(account);
    }

    public BigDecimal getBalance(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account Not Found"));
        return account.getBalance();
    }

    /*
    Blocked Account - Called by fraud-detection-service via kafka
        @params accountNumber
     */
    public void blockAccount(String accountNumber) {
        log.info("Blocking Account : {}", accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account Not Found"));
        account.setAccountStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);
        log.info("Account Blocked : {}", accountNumber);
    }


    /*
    Deduct Balance from Sender Account
    Called by transaction-service
     */
    public void deductBalance(String accountNumber, BigDecimal amount) {
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account Not Found"));
        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Account is Not Account : " + accountNumber);
        }

        if(account.getBalance().compareTo(amount) < 0){
            throw new RuntimeException("Insufficient Funds for Account" + accountNumber);
        }

        account.setBalance(account.getBalance().subtract(amount));
        accountRepository.save(account);
        log.info("Balance Updated. New Balance is {} ", account.getBalance());
    }

    /*
    Credit Balance
    Called by transaction-service via kafka
     */
    public void creditBalance(String accountNumber, BigDecimal amount) {
        log.info("Crediting {} to account : {}", amount,accountNumber);
        Account account = accountRepository.findByAccountNumber(accountNumber).orElseThrow(() -> new RuntimeException("Account Not Found"));
        if(account.getAccountStatus() != AccountStatus.ACTIVE){
            throw new RuntimeException("Account is Not Account : " + accountNumber);
        }
        account.setBalance(account.getBalance().add(amount));
        log.info("Balance credited. New Balance : {}",account.getBalance());
    }
}
