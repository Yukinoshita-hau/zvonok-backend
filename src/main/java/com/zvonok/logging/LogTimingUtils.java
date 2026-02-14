package com.zvonok.logging;

public class LogTimingUtils {
	public static long calculateDurationDifference(long durationStart) {
		return System.currentTimeMillis() - durationStart;
	}
}
