package com.nurseli.nrsfinanceportal;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class NrsFinancePortalApplication {

    public static void main(String[] args) {
        SpringApplication.run(NrsFinancePortalApplication.class, args);
    }
}