package com.cashbank;

import org.springframework.boot.SpringApplication;

public class TestCashbankApiApplication {

	public static void main(String[] args) {
		SpringApplication.from(CashbankApiApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
