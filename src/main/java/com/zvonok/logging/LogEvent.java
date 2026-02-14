package com.zvonok.logging;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class LogEvent {
	String action;
	String result;
	Long userId;
	Long serverId;
	Long roomId;
	Long channelId;
	Long folderId;
	Long messageId;
	Long permission;
	String error;
	Long duration;

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		first = appendField(first, sb, "action", action);
		first = appendField(first, sb, "result", result);
		first = appendField(first, sb, "userId", userId);
		first = appendField(first, sb, "serverId", serverId);
		first = appendField(first, sb, "roomId", roomId);
		first = appendField(first, sb, "channelId", channelId);
		first = appendField(first, sb, "folderId", folderId);
		first = appendField(first, sb, "messageId", messageId);
		first = appendField(first, sb, "permission", permission);
		first = appendField(first, sb, "error", error);
		first = appendField(first, sb, "duration", duration);
		return sb.toString();
	}

	private void addSeparator(StringBuilder stringBuilder) {
		stringBuilder.append(" | ");
	}

	private boolean appendField(boolean first, StringBuilder sb, String key, Object value) {
		if (value == null) {
			return first;
		}
		if (!first) {
			addSeparator(sb);
		}
		sb.append(key).append("=").append(value);
		return false;
	}

	public static LogEvent.LogEventBuilder buildFailedEvent(String action, String error, long duration) {
		return LogEvent.builder().action(action).result(LogEventConstants.EVENT_FAILED_RESULT)
				.error(error).duration(duration);
	}

	public static LogEvent.LogEventBuilder buildSuccessEvent(String action, Long duration) {
		return LogEvent.builder().action(action).result(LogEventConstants.EVENT_SUCCES_RESULT).duration(duration);
	}
}
