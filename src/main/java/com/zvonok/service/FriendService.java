package com.zvonok.service;

import com.zvonok.controller.dto.ChatErrorMessageResponse;
import com.zvonok.controller.dto.FriendRequestResponse;
import com.zvonok.exception.*;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.FriendRequest;
import com.zvonok.model.Friendship;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.FriendRequestStatus;
import com.zvonok.repository.FriendRequestRepository;
import com.zvonok.repository.FriendshipRepository;
import com.zvonok.service.dto.FriendEventMessage;
import com.zvonok.service.enums.BrokerPath;
import com.zvonok.service.enums.FriendEventType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class FriendService {

	private final UserService userService;
	private final FriendRequestRepository friendRequestRepository;
	private final FriendshipRepository friendshipRepository;
	private final SimpMessagingTemplate simpMessagingTemplate;
	private final RoomService roomService;

	public List<Friendship> getFriendships(Long userId) {
		ensureUserExists(userId);
		return friendshipRepository.findByUserOneIdOrUserTwoId(userId, userId);
	}

	public List<FriendRequest> getIncomingRequests(Long userId) {
		ensureUserExists(userId);
		return friendRequestRepository.findByReceiverIdAndStatus(userId,
				FriendRequestStatus.PENDING);
	}

	public List<FriendRequest> getOutgoingRequests(Long userId) {
		ensureUserExists(userId);
		return friendRequestRepository.findBySenderIdAndStatus(userId, FriendRequestStatus.PENDING);
	}

	@Transactional
	public void sendFriendRequest(String senderUsername, String receiverUsername) {
		if (senderUsername.equals(receiverUsername)) {
			throw new CannotAddYourselfAsFriendException(
					HttpResponseMessage.HTTP_CANNOT_ADD_SELF_AS_FRIEND_RESPONSE_MESSAGE
							.getMessage());
		}

		User sender = getUser(senderUsername);
		User receiver = getUser(receiverUsername);

		ensureUsersAreNotFriends(sender.getId(), receiver.getId());

		EnumSet<FriendRequestStatus> pendingStatus = EnumSet.of(FriendRequestStatus.PENDING);
		if (friendRequestRepository.existsBySenderIdAndReceiverIdAndStatusIn(sender.getId(),
				receiver.getId(), pendingStatus)
				|| friendRequestRepository.existsBySenderIdAndReceiverIdAndStatusIn(
						receiver.getId(), sender.getId(), pendingStatus)) {
			throw new FriendRequestAlreadyExistsException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ALREADY_EXISTS_RESPONSE_MESSAGE
							.getMessage());
		}

		FriendRequest friendRequest = FriendRequest.builder().sender(sender).receiver(receiver)
				.status(FriendRequestStatus.PENDING).build();

		friendRequestRepository.save(friendRequest);

		FriendEventMessage response =
				toFriendEventMessage(friendRequest, FriendEventType.FRIEND_REQUEST_CREATED);

		simpMessagingTemplate.convertAndSendToUser(friendRequest.getSender().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);

		simpMessagingTemplate.convertAndSendToUser(friendRequest.getReceiver().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);

	}

	@Transactional
	public void acceptFriendRequest(Long requestId, String receiverUsername) {
		User receiver = userService.getUser(receiverUsername);

		FriendRequest friendRequest = getPendingFriendRequest(requestId);

		if (!friendRequest.getReceiver().getId().equals(receiver.getId())) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}

		friendRequest.setStatus(FriendRequestStatus.ACCEPTED);
		friendRequestRepository.save(friendRequest);

		createFriendship(friendRequest.getSender(), friendRequest.getReceiver());
		roomService.createOrGetPrivateRoom(friendRequest.getSender().getUsername(),
				friendRequest.getReceiver().getUsername());

		FriendEventMessage response =
				toFriendEventMessage(friendRequest, FriendEventType.FRIEND_REQUEST_ACCEPTED);

		simpMessagingTemplate.convertAndSendToUser(friendRequest.getSender().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);


		simpMessagingTemplate.convertAndSendToUser(friendRequest.getReceiver().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);
	}

	@Transactional
	public void rejectFriendRequest(Long requestId, String receiverUsername) {
		User receiver = userService.getUser(receiverUsername);

		FriendRequest friendRequest = getPendingFriendRequest(requestId);

		if (!friendRequest.getReceiver().getId().equals(receiver.getId())) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}

		friendRequest.setStatus(FriendRequestStatus.REJECTED);
		friendRequestRepository.save(friendRequest);


		FriendEventMessage response =
				toFriendEventMessage(friendRequest, FriendEventType.FRIEND_REQUEST_REJECTED);

		simpMessagingTemplate.convertAndSendToUser(friendRequest.getSender().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);


		simpMessagingTemplate.convertAndSendToUser(friendRequest.getReceiver().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);
	}

	@Transactional
	public void cancelFriendRequest(Long requestId, String senderUsername) {
		User sender = userService.getUser(senderUsername);

		FriendRequest friendRequest = getPendingFriendRequest(requestId);

		if (!friendRequest.getSender().getId().equals(sender.getId())) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}

		friendRequest.setStatus(FriendRequestStatus.CANCELLED);
		friendRequestRepository.save(friendRequest);


		FriendEventMessage response =
				toFriendEventMessage(friendRequest, FriendEventType.FRIEND_REQUEST_CANCELLED);

		simpMessagingTemplate.convertAndSendToUser(friendRequest.getSender().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);


		simpMessagingTemplate.convertAndSendToUser(friendRequest.getReceiver().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);
	}

	@Transactional
	public void removeFriend(String userUsername, String friendUsername) {
		User user = userService.getUser(userUsername);
		User friend = userService.getUser(friendUsername);

		Long[] normalizedPair = normalizePair(user.getId(), friend.getId());

		Friendship friendship = friendshipRepository
				.findByUserOneIdAndUserTwoId(normalizedPair[0], normalizedPair[1])
				.orElseThrow(() -> new FriendshipNotFoundException(
						HttpResponseMessage.HTTP_FRIENDSHIP_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));

		friendshipRepository.delete(friendship);

		FriendEventMessage response = toFriendEventMessage(null, FriendEventType.FRIEND_DELETE);

		simpMessagingTemplate.convertAndSendToUser(friendship.getUserOne().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);


		simpMessagingTemplate.convertAndSendToUser(friendship.getUserTwo().getUsername(),
				BrokerPath.FRIEND_REQUESTS_QUEUE_PATH.getPath(), response);
	}

	public boolean areFriends(Long firstUserId, Long secondUserId) {
		Long[] normalizedPair = normalizePair(firstUserId, secondUserId);
		return friendshipRepository.existsByUserOneIdAndUserTwoId(normalizedPair[0],
				normalizedPair[1]);
	}

	public void sendErrorMessage(String username, String message, HttpStatus status) {
		ChatErrorMessageResponse messageResponse = new ChatErrorMessageResponse();
		messageResponse.setMessage(message);
		messageResponse.setStatus(status.value());
		simpMessagingTemplate.convertAndSendToUser(username, BrokerPath.ERRORS_QUEUE_PATH.getPath(),
				messageResponse);
	}

	private FriendRequest getPendingFriendRequest(Long requestId) {
		FriendRequest friendRequest = friendRequestRepository.findById(requestId)
				.orElseThrow(() -> new FriendRequestNotFoundException(
						HttpResponseMessage.HTTP_FRIEND_REQUEST_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));

		if (friendRequest.getStatus() != FriendRequestStatus.PENDING) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}
		return friendRequest;
	}


	private FriendRequestResponse toFriendRequestResponse(FriendRequest friendRequest) {
		return FriendRequestResponse.builder().requestId(friendRequest.getId())
				.senderId(friendRequest.getSender().getId())
				.senderUsername(friendRequest.getSender().getUsername())
				.senderAvatarUrl(friendRequest.getSender().getAvatarUrl())
				.receiverId(friendRequest.getReceiver().getId())
				.receiverUsername(friendRequest.getReceiver().getUsername())
				.receiverAvatarUrl(friendRequest.getReceiver().getAvatarUrl())
				.status(friendRequest.getStatus()).createdAt(friendRequest.getCreatedAt())
				.updatedAt(friendRequest.getUpdatedAt()).build();
	}

	private FriendEventMessage toFriendEventMessage(FriendRequest friendRequest,
			FriendEventType type) {
		if (friendRequest == null) {
			return FriendEventMessage.builder().type(type).payload(null).build();
		} else {
			return FriendEventMessage.builder().type(type)
					.payload(toFriendRequestResponse(friendRequest)).build();
		}
	}


	private Friendship createFriendship(User firstUser, User secondUser) {
		Long[] normalizedPair = normalizePair(firstUser.getId(), secondUser.getId());

		Optional<Friendship> existing = friendshipRepository
				.findByUserOneIdAndUserTwoId(normalizedPair[0], normalizedPair[1]);
		if (existing.isPresent()) {
			return existing.get();
		}

		User userOne = firstUser.getId().equals(normalizedPair[0]) ? firstUser : secondUser;
		User userTwo = userOne == firstUser ? secondUser : firstUser;

		Friendship friendship = Friendship.builder().userOne(userOne).userTwo(userTwo).build();

		return friendshipRepository.save(friendship);
	}

	private void ensureUsersAreNotFriends(Long firstUserId, Long secondUserId) {
		if (areFriends(firstUserId, secondUserId)) {
			throw new FriendshipAlreadyExistsException(
					HttpResponseMessage.HTTP_FRIENDSHIP_ALREADY_EXISTS_RESPONSE_MESSAGE
							.getMessage());
		}
	}

	private void ensureUserExists(Long userId) {
		try {
			userService.getUser(userId);
		} catch (UserNotFoundException e) {
			throw new UserNotFoundException(
					HttpResponseMessage.HTTP_USER_NOT_FOUND_RESPONSE_MESSAGE.getMessage());
		}
	}

	private User getUser(Long userId) {
		return userService.getUser(userId);
	}

	private User getUser(String usernameOrEmail) {
		return userService.getUser(usernameOrEmail);
	}

	private Long[] normalizePair(Long first, Long second) {
		return first <= second ? new Long[] {first, second} : new Long[] {second, first};
	}
}

