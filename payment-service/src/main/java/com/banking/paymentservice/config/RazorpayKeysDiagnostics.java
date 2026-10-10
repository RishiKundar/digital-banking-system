package com.banking.paymentservice.config;


import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Component;


@Component
public class RazorpayKeysDiagnostics {

    public RazorpayKeysDiagnostics(ConfigurableEnvironment env) {
        System.out.println("Resolved key: "
                + env.getProperty("razorpay.key-id"));

        for (PropertySource<?> source : env.getPropertySources()) {
            Object value = source.getProperty("razorpay.key-id");

            if (value != null) {
                System.out.println(
                        source.getName() + " -> " + value
                );
            }
        }
    }
}
