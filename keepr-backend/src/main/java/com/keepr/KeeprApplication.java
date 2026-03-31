package com.keepr;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Keepr application.
 */
@SpringBootApplication
public class KeeprApplication {

    /**
     * Starts the Keepr Spring Boot application.
     *
     * @param args command-line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(KeeprApplication.class, args);
    }
}
