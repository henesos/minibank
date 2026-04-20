package com.minibank.account;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;

/**
 * MiniBank Account Service Application
 *
 * Handles account management and balance operations.
 * Runs on port 8082 by default.
 *
 * Key Features:
 * - Account creation (savings, checking)
 * - Balance management (atomic updates, never cached)
 * - Account status management
 *
 * @author MiniBank Team
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableKafka
public class AccountServiceApplication {

    /** Main entry point. @param args command-line arguments */
    public static void main(String[] args) {
        SpringApplication.run(AccountServiceApplication.class, args);
    }
}
