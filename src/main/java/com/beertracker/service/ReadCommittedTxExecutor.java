package com.beertracker.service;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/**
 * Executes domain write operations within an ACID transaction at
 * {@link TransactionDefinition#ISOLATION_READ_COMMITTED} isolation level ({@code REQ-NF-007}).
 *
 * <p>Using an explicit {@link TransactionTemplate} ensures that when a service coordinates
 * concurrency via per-resource monitor locks, the database transaction commits strictly before
 * the lock is released, preventing uncommitted reads or lost counter updates.</p>
 */
@Component
public class ReadCommittedTxExecutor {

    private final TransactionTemplate readCommittedTemplate;

    /**
     * Constructs the executor using the configured Spring transaction manager.
     *
     * @param transactionManager platform transaction manager backing JPA persistence
     */
    public ReadCommittedTxExecutor(PlatformTransactionManager transactionManager) {
        this.readCommittedTemplate = new TransactionTemplate(transactionManager);
        this.readCommittedTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Executes a returning unit of work inside a {@code READ_COMMITTED} ACID transaction.
     *
     * @param action business operation to execute atomically
     * @param <T>    return type of the operation
     * @return result produced by {@code action}
     */
    public <T> T execute(Supplier<T> action) {
        return readCommittedTemplate.execute(status -> action.get());
    }

    /**
     * Executes a void unit of work inside a {@code READ_COMMITTED} ACID transaction.
     *
     * @param action business operation to execute atomically
     */
    public void executeVoid(Runnable action) {
        readCommittedTemplate.executeWithoutResult(status -> action.run());
    }
}
