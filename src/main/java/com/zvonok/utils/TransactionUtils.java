package com.zvonok.utils;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class TransactionUtils {

	private TransactionUtils() {}

	/*
		Крч метод что выполнения деёствия после успешного комита	 
		если транзакции нема то тупо сразу выполнет действие	
	*/
	public static void runAfterCommit(Runnable action) {
		if (action == null) {
			throw new IllegalArgumentException("Action must be not null");
		}

		if (TransactionSynchronizationManager.isActualTransactionActive()) {
			TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
				@Override
				public void afterCommit() {
					try {
						action.run();
					} catch (Exception e) {
						log.error("Error in afterCommit action: {}", e.getMessage());						
					}
				};
			});	
		} else {
			action.run();
		}
	}
}
