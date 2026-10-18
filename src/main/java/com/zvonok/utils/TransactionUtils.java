package com.zvonok.utils;

import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TransactionUtils {

	private TransactionUtils() {}

    /** Publishes after successful commit; without a transaction, executes immediately. */
	public static void runAfterCommit(Runnable action) {
		if (action == null) {
			throw new IllegalArgumentException("Action must be not null");
		}

		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			AtomicBoolean executed = new AtomicBoolean();
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					if (!executed.compareAndSet(false, true)) return;
					try {
						action.run();
					} catch (Exception e) {
						log.error("Error in afterCommit action", e);
					}
				}
			});
		} else {
			action.run();
		}
	}
}
