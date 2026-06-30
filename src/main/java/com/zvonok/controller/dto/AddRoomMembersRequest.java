package com.zvonok.controller.dto;

import java.util.List;

public record AddRoomMembersRequest(
		List<Long> userIds
) {
}
