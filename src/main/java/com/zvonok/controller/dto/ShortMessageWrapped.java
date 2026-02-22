package com.zvonok.controller.dto;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ShortMessageWrapped {
	private final Long id;
    private final String content;
	private final MessageType type;
	private final LocalDateTime sentAt;
	private final SenderDto sender;
	private final RoomShortDto room;
}
