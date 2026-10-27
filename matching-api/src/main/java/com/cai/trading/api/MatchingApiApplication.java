package com.cai.trading.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.cai.trading")
public class MatchingApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(MatchingApiApplication.class, args);
    }
}

