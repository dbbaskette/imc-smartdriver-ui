package com.insurancemegacorp.monitoring;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartDriverMonitoringApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartDriverMonitoringApplication.class, args);
    }
}