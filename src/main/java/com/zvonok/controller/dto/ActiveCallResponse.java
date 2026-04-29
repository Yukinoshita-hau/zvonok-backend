package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;

@Builder
public record ActiveCallResponse(
        Long callId,
        Long chatRoomId,
        Long roomId,
        RoomType roomType,
        CallSessionStatus status,
        String liveKitRoomName,
        String hostUsername,
        String callerUsername,
        String callType,
        int participantsCount,
        List<CallParticipantResponse> participants,
        LocalDateTime startedAt,
        LocalDateTime activatedAt,
        LocalDateTime createdAt
) {
}
