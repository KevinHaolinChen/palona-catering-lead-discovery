package com.palona.cateringleads;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;
@EnableAsync
@SpringBootApplication
public class CateringLeadApplication {
    public static void main(String[] args) { SpringApplication.run(CateringLeadApplication.class, args); }
}