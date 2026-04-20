package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.MessageType;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class ReplyPreviewDto {

	private final Long id;
	private final Long authorId;
	private final String authorUsername;
	private final String authorDisplayName;
	private final String snippet;
	private final MessageType type;
	private final boolean deleted;
}
