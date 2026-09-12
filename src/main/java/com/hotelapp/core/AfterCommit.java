package com.hotelapp.core;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Run a task after the surrounding transaction commits — the port's form of
 * upstream's "post-commit, best-effort" blocks (guest emails queued after the
 * booking/payment change they describe has committed). Runs inline when no
 * transaction synchronization is active.
 */
public final class AfterCommit {

    private AfterCommit() {
    }

    public static void run(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            task.run();
                        }
                    });
        } else {
            task.run();
        }
    }
}
