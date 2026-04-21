package com.minibank.transaction;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * MiniBank Transaction Service Application
 * 
 * Handles money transfers with Saga Orchestrator pattern.
 * Runs on port 8083 by default.
 * 
 * Key Features:
 * - Money transfers between accounts
 * - Saga Orchestrator for distributed transactions
 * - Outbox Pattern for reliable messaging
 * - Distributed Idempotency
 * 
 * @author MiniBank Team
 */
@SpringBootApplication
@EnableJpaAuditing
@EnableKafka
@EnableScheduling
public class TransactionServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(TransactionServiceApplication.class, args);
    }
}
