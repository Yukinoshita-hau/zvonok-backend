package com.zvonok.service.interfaces;

import com.zvonok.model.CallSession;
import com.zvonok.service.enums.CallRecordingAction;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class CallRecordingPayload {
	private CallRecordingAction action;
	private Long sessionId;
}
