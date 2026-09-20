package com.palona.cateringleads.service;

import java.time.Duration;
import java.util.function.Supplier;

final class RetryExecutor {
    private RetryExecutor() {}

    static <T> T withBackoff(int maxAttempts, Duration initialDelay, Supplier<T> operation) {
        RuntimeException last = null;
        long delayMillis = initialDelay.toMillis();
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return operation.get();
            } catch (RuntimeException exception) {
                last = exception;
                if (attempt == maxAttempts) break;
                try {
                    Thread.sleep(delayMillis);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("Retry interrupted", interrupted);
                }
                delayMillis = Math.min(delayMillis * 2, 4_000);
            }
        }
        throw last == null ? new IllegalStateException("Operation failed") : last;
    }
}
