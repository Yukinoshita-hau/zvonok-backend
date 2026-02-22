package com.zvonok.controller;

import com.zvonok.controller.dto.FriendRequestResponse;
import com.zvonok.controller.dto.FriendResponse;
import com.zvonok.controller.dto.SendFriendRequest;
import com.zvonok.documentation.FriendApiDescriptions;
import com.zvonok.documentation.UserApiDescriptions;
import com.zvonok.documentation.annotation.ApiResponse400;
import com.zvonok.documentation.annotation.ApiResponse403;
import com.zvonok.documentation.annotation.ApiResponse404;
import com.zvonok.documentation.annotation.ApiResponse409;
import com.zvonok.documentation.annotation.SecuredApiResponses;
import com.zvonok.model.FriendRequest;
import com.zvonok.model.Friendship;
import com.zvonok.model.User;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.FriendService;
import com.zvonok.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;


@Tag(name = "Контроллер друзей",
		description = "Эндпоинты для управления друзьями и заявками в друзья: список друзей, входящие/исходящие заявки, отправка, принятие, отклонение, отмена заявки и удаление друга.")
@SecurityRequirement(name = "JWT")
@RestController
@RequestMapping("/friends")
@RequiredArgsConstructor
public class FriendController {

	private final FriendService friendService;
	private final UserService userService;

	@Operation(summary = "Список друзей",
			description = "Возвращает список друзей текущего пользователя.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200", description = FriendApiDescriptions.FRIEND_GET_LIST_SUCCESS)
	@GetMapping
	public ResponseEntity<List<FriendResponse>> getFriends(
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		List<FriendResponse> friends = friendService.getFriendships(currentUser.getId()).stream()
				.map(friendship -> toFriendResponse(friendship, currentUser)).toList();
		return ResponseEntity.ok(friends);
	}

	@Operation(summary = "Входящие заявки в друзья",
			description = "Возвращает список входящих заявок в друзья для текущего пользователя.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = FriendApiDescriptions.FRIEND_GET_INCOMING_REQUESTS_SUCCESS)
	@GetMapping("/requests/incoming")
	public ResponseEntity<List<FriendRequestResponse>> getIncomingRequests(
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		List<FriendRequestResponse> requests =
				friendService.getIncomingRequests(currentUser.getId()).stream()
						.map(this::toFriendRequestResponse).toList();
		return ResponseEntity.ok(requests);
	}

	@Operation(summary = "Исходящие заявки в друзья",
			description = "Возвращает список исходящих заявок в друзья для текущего пользователя.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = FriendApiDescriptions.FRIEND_GET_OUTGOING_REQUESTS_SUCCESS)
	@GetMapping("/requests/outgoing")
	public ResponseEntity<List<FriendRequestResponse>> getOutgoingRequests(
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		List<FriendRequestResponse> requests =
				friendService.getOutgoingRequests(currentUser.getId()).stream()
						.map(this::toFriendRequestResponse).toList();
		return ResponseEntity.ok(requests);
	}

	@Operation(summary = "Отправить заявку в друзья",
			description = "Создаёт заявку в друзья от текущего пользователя к пользователю receiverUsername. Возвращает созданную заявку.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = FriendApiDescriptions.FRIEND_SEND_REQUEST_SUCCESS)
	@ApiResponse400
	@ApiResponse404(description = UserApiDescriptions.USER_NOT_FOUND)
	@ApiResponse409(description = FriendApiDescriptions.FRIEND_REQUEST_ALREADY_EXIST)
	@PostMapping("/requests")
	public ResponseEntity<FriendRequestResponse> sendFriendRequest(
			@Valid @RequestBody SendFriendRequest request,
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		User receiver = userService.getUser(request.getReceiverUsername());
		FriendRequest friendRequest =
				friendService.sendFriendRequest(currentUser.getId(), receiver.getId());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(toFriendRequestResponse(friendRequest));
	}

	@Operation(summary = "Принять заявку в друзья",
			description = "Принимает входящую заявку requestId текущим пользователем и создаёт дружбу. Возвращает данные дружбы.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = FriendApiDescriptions.FRIEND_ACCEPT_REQUEST_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = FriendApiDescriptions.FRIEND_REQUEST_NOT_FOUND)
	@PostMapping("/requests/{requestId}/accept")
	public ResponseEntity<FriendResponse> acceptFriendRequest(@PathVariable Long requestId,
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		Friendship friendship = friendService.acceptFriendRequest(requestId, currentUser.getId());
		return ResponseEntity.ok(toFriendResponse(friendship, currentUser));
	}

	@Operation(summary = "Отклонить заявку в друзья",
			description = "Отклоняет заявку requestId текущим пользователем. Возвращает обновлённое состояние заявки.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = FriendApiDescriptions.FRIEND_REJECT_REQUEST_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = FriendApiDescriptions.FRIEND_REQUEST_NOT_FOUND)
	@PostMapping("/requests/{requestId}/reject")
	public ResponseEntity<FriendRequestResponse> rejectFriendRequest(@PathVariable Long requestId,
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		FriendRequest friendRequest =
				friendService.rejectFriendRequest(requestId, currentUser.getId());
		return ResponseEntity.ok(toFriendRequestResponse(friendRequest));
	}

	@Operation(summary = "Отменить исходящую заявку",
			description = "Отменяет (cancels) исходящую заявку requestId текущим пользователем. Возвращает обновлённое состояние заявки.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "200",
			description = FriendApiDescriptions.FRIEND_CANCEL_REQUEST_SUCCESS)
	@ApiResponse403
	@ApiResponse404(description = FriendApiDescriptions.FRIEND_REQUEST_NOT_FOUND)
	@PostMapping("/requests/{requestId}/cancel")
	public ResponseEntity<FriendRequestResponse> cancelFriendRequest(@PathVariable Long requestId,
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		FriendRequest friendRequest =
				friendService.cancelFriendRequest(requestId, currentUser.getId());
		return ResponseEntity.ok(toFriendRequestResponse(friendRequest));
	}

	@Operation(summary = "Удалить друга",
			description = "Удаляет пользователя friendId из друзей текущего пользователя.")
	@SecuredApiResponses
	@ApiResponse(responseCode = "204", description = FriendApiDescriptions.FRIEND_DELETE_SUCCESS)
	@ApiResponse404(description = FriendApiDescriptions.FRIEND_FRIENDSHIP_NOT_FOUND)
	@DeleteMapping("/{friendId}")
	public ResponseEntity<Void> removeFriend(@PathVariable Long friendId,
			@AuthenticationPrincipal UserPrincipal principal) {
		User currentUser = getCurrentUser(principal);
		friendService.removeFriend(currentUser.getId(), friendId);
		return ResponseEntity.noContent().build();
	}

	private FriendResponse toFriendResponse(Friendship friendship, User currentUser) {
		User friend = Objects.equals(friendship.getUserOne().getId(), currentUser.getId())
				? friendship.getUserTwo()
				: friendship.getUserOne();

		return FriendResponse.builder().friendshipId(friendship.getId()).friendId(friend.getId())
				.friendUsername(friend.getUsername()).friendAvatarUrl(friend.getAvatarUrl())
				.friendStatus(friend.getStatus()).friendshipSince(friendship.getCreatedAt())
				.build();
	}

	private FriendRequestResponse toFriendRequestResponse(FriendRequest friendRequest) {
		return FriendRequestResponse.builder().requestId(friendRequest.getId())
				.senderId(friendRequest.getSender().getId())
				.senderUsername(friendRequest.getSender().getUsername())
				.receiverId(friendRequest.getReceiver().getId())
				.receiverUsername(friendRequest.getReceiver().getUsername())
				.status(friendRequest.getStatus()).createdAt(friendRequest.getCreatedAt())
				.updatedAt(friendRequest.getUpdatedAt()).build();
	}

	private User getCurrentUser(UserPrincipal principal) {
		return userService.getUser(principal.getUsername());
	}
}

