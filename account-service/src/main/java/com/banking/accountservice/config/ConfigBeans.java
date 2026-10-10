package com.banking.accountservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.SecureRandom;

@Configuration
public class ConfigBeans {

    @Bean
    public SecureRandom secureRandom(){
        return new SecureRandom();
    }
}
