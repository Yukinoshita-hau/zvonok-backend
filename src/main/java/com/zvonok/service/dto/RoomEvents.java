package com.zvonok.service.dto;

import com.zvonok.service.enums.RoomEventsType;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class RoomEvents {
	RoomEventsType type;
	Object payload;
}
