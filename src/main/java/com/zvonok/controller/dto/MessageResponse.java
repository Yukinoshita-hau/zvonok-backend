package com.zvonok.controller.dto;

import com.zvonok.model.enumeration.MessageType;
import com.zvonok.service.dto.EventType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Schema(name = "MessageResponse", description = "Ответ с сообщением.")
@Data
public class MessageResponse {
	@Schema(description = "ID сообщения.", example = "123",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private long id;

	@Schema(description = "Текст сообщения.", example = "Привет!",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String content;

	@Schema(description = "Username отправителя.", example = "Murat",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private String senderUsername;

	@Schema(description = "Дата и время отправки (формат ISO-8601).",
			example = "2026-02-01T16:55:00", accessMode = Schema.AccessMode.READ_ONLY,
			requiredMode = Schema.RequiredMode.REQUIRED)
	private LocalDateTime sentAt;

	@Schema(description = "Тип сообщения.", example = "TEXT",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private MessageType messageType;

	@Schema(description = "ID комнаты, в которой находится сообщение.", example = "77",
			accessMode = Schema.AccessMode.READ_ONLY, requiredMode = Schema.RequiredMode.REQUIRED)
	private Long roomId;

	@Schema(description = "Тип события (если сообщение системное/событийное).",
			example = "MESSAGE_CREATED", accessMode = Schema.AccessMode.READ_ONLY)
	private EventType eventType;

	@Schema(description = "ID родительского сообщения для reply (если есть).", example = "321",
			accessMode = Schema.AccessMode.READ_ONLY)
	private Long replyToMessageId;

	@Schema(description = "Компактный preview родительского сообщения для reply.",
			accessMode = Schema.AccessMode.READ_ONLY)
	private ReplyPreviewDto replyPreview;

	private List<MessageAttachmentDto> attachments;
}
