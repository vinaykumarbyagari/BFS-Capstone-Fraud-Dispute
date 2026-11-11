package com.capstone.fraud;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FraudDisputeApplication {

    public static void main(String[] args) {
        SpringApplication.run(FraudDisputeApplication.class, args);
    }
}
