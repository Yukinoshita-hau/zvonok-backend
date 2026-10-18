package com.zvonok.unittests.service;

import com.zvonok.utils.TransactionUtils;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import static org.junit.jupiter.api.Assertions.*;

class TransactionPublicationTest {
    @Test void callbackRunsOnlyOnceEvenWhenInvokedTwice() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            AtomicInteger calls = new AtomicInteger();
            TransactionUtils.runAfterCommit(calls::incrementAndGet);
            assertEquals(0, calls.get());
            var callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
            callback.afterCommit(); callback.afterCommit();
            assertEquals(1, calls.get());
        } finally { TransactionSynchronizationManager.clear(); }
    }
    @Test void failedPublicationIsNotRetriedByDuplicateCallback() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            AtomicInteger calls = new AtomicInteger();
            TransactionUtils.runAfterCommit(() -> { calls.incrementAndGet(); throw new IllegalStateException("broker unavailable"); });
            var callback = TransactionSynchronizationManager.getSynchronizations().getFirst();
            assertDoesNotThrow(callback::afterCommit); callback.afterCommit();
            assertEquals(1, calls.get());
        } finally { TransactionSynchronizationManager.clear(); }
    }
}
