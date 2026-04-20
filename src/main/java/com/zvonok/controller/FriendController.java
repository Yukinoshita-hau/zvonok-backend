package com.zvonok.controller;

import com.zvonok.controller.dto.FriendRequestResponse;
import com.zvonok.controller.dto.FriendResponse;
import com.zvonok.documentation.FriendApiDescriptions;
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
import lombok.RequiredArgsConstructor;
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
	@GetMapping()
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

	private FriendResponse toFriendResponse(Friendship friendship, User currentUser) {
		User friend = Objects.equals(friendship.getUserOne().getId(), currentUser.getId())
				? friendship.getUserTwo()
				: friendship.getUserOne();

		return FriendResponse.builder().friendshipId(friendship.getId()).friendId(friend.getId())
				.friendUsername(friend.getUsername()).friendDisplayName(friend.getDisplayName()).friendAvatarUrl(friend.getAvatarUrl())
				.friendStatus(friend.getStatus()).friendshipSince(friendship.getCreatedAt())
				.build();
	}

	private FriendRequestResponse toFriendRequestResponse(FriendRequest friendRequest) {
		return FriendRequestResponse.builder().requestId(friendRequest.getId())
				.senderId(friendRequest.getSender().getId())
				.senderUsername(friendRequest.getSender().getUsername())
				.senderDisplayName(friendRequest.getSender().getDisplayName())
				.senderAvatarUrl(friendRequest.getSender().getAvatarUrl())
				.receiverId(friendRequest.getReceiver().getId())
				.receiverUsername(friendRequest.getReceiver().getUsername())
				.status(friendRequest.getStatus()).createdAt(friendRequest.getCreatedAt())
				.updatedAt(friendRequest.getUpdatedAt()).build();
	}

	private User getCurrentUser(UserPrincipal principal) {
		return userService.getUser(principal.getUsername());
	}
}

