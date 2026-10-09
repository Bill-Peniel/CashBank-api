package com.cashbank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class CashbankApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CashbankApiApplication.class, args);
    }

}
