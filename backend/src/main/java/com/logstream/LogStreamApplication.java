package com.logstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class LogStreamApplication {
    public static void main(String[] args) {
        SpringApplication.run(LogStreamApplication.class, args);
    }
}
