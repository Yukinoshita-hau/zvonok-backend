package com.zvonok.controller;

import com.zvonok.controller.dto.MessageResponse;
import com.zvonok.controller.dto.UpdateMessageRequest;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.model.Message;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Контроллер сообщений",
		description = "Эндпоинты для получения, редактирования и удаления сообщений.")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/message")
@RequiredArgsConstructor
public class MessageController {

	private final MessageService messageService;

	@Operation(summary = "Получить сообщение", description = "Возвращает сообщение по messageId.")
	@SecuredApiResponses
	@GetMapping("/{messageId}")
	public ResponseEntity<Message> getMessage(@PathVariable Long messageId) {
		return ResponseEntity.ok(messageService.getMessage(messageId));
	}

	@Operation(summary = "Редактировать сообщение",
			description = "Обновляет содержимое сообщения по messageId от имени текущего пользователя. "
					+ "Возвращает обновлённое сообщение (в виде MessageResponse).")
	@SecuredApiResponses
	@PutMapping("/{messageId}")
	public ResponseEntity<MessageResponse> updateMessage(@PathVariable Long messageId,
			@Valid @RequestBody UpdateMessageRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		MessageResponse response = messageService.editMessage(messageId, principal.getUsername(),
				request.getContent());
		return ResponseEntity.ok(response);
	}

	@Operation(summary = "Удалить сообщение",
			description = "Удаляет сообщение по messageId от имени текущего пользователя (обычно soft-delete).")
	@SecuredApiResponses
	@DeleteMapping("/{messageId}")
	public ResponseEntity<Void> deleteMessage(@PathVariable Long messageId,
			@AuthenticationPrincipal UserPrincipal principal) {
		messageService.deleteMessage(messageId, principal.getUsername());
		return ResponseEntity.noContent().build();
	}
}

