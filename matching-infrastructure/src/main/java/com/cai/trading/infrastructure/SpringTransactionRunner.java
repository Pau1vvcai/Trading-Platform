package com.cai.trading.infrastructure;

import com.cai.trading.application.TransactionRunner;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.function.Supplier;

/** 将 Spring 事务能力适配为应用层定义的端口。 */
public final class SpringTransactionRunner implements TransactionRunner {
    private final TransactionTemplate transactionTemplate;

    public SpringTransactionRunner(TransactionTemplate transactionTemplate) {
        this.transactionTemplate = java.util.Objects.requireNonNull(transactionTemplate);
    }

    @Override
    public <T> T inTransaction(Supplier<T> action) {
        T result = transactionTemplate.execute(status -> action.get());
        if (result == null) {
            throw new IllegalStateException("Transaction returned null result");
        }
        return result;
    }
}

