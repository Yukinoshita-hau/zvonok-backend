package com.zvonok.controller.dto;

import java.util.List;

public record AddRoomMembersResponse(
		Long roomId,
		List<AddedRoomMemberDto> addedMembers,
		List<SkippedRoomMemberDto> skipped
) {
}
