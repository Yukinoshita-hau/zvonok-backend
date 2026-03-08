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
@Transactional(readOnly = true)
public class FriendService {

	private final UserService userService;
	private final FriendRequestRepository friendRequestRepository;
	private final FriendshipRepository friendshipRepository;
	private final SimpMessagingTemplate simpMessagingTemplate;

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

		FriendRequestResponse response = toFriendRequestResponse(friendRequest);

		simpMessagingTemplate.convertAndSendToUser(response.getSenderUsername(),
				"/queue/friend-requests", response);

		simpMessagingTemplate.convertAndSendToUser(response.getReceiverUsername(),
				"/queue/friend-requests", response);

	}

	@Transactional
	public Friendship acceptFriendRequest(Long requestId, Long receiverId) {
		FriendRequest friendRequest = getPendingFriendRequest(requestId);

		if (!friendRequest.getReceiver().getId().equals(receiverId)) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}

		friendRequest.setStatus(FriendRequestStatus.ACCEPTED);
		friendRequestRepository.save(friendRequest);

		return createFriendship(friendRequest.getSender(), friendRequest.getReceiver());
	}

	@Transactional
	public FriendRequest rejectFriendRequest(Long requestId, Long receiverId) {
		FriendRequest friendRequest = getPendingFriendRequest(requestId);

		if (!friendRequest.getReceiver().getId().equals(receiverId)) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}

		friendRequest.setStatus(FriendRequestStatus.REJECTED);
		return friendRequestRepository.save(friendRequest);
	}

	@Transactional
	public FriendRequest cancelFriendRequest(Long requestId, Long senderId) {
		FriendRequest friendRequest = getPendingFriendRequest(requestId);

		if (!friendRequest.getSender().getId().equals(senderId)) {
			throw new FriendRequestActionNotAllowedException(
					HttpResponseMessage.HTTP_FRIEND_REQUEST_ACTION_NOT_ALLOWED_RESPONSE_MESSAGE
							.getMessage());
		}

		friendRequest.setStatus(FriendRequestStatus.CANCELLED);
		return friendRequestRepository.save(friendRequest);
	}

	@Transactional
	public void removeFriend(Long userId, Long friendId) {
		Long[] normalizedPair = normalizePair(userId, friendId);

		Friendship friendship = friendshipRepository
				.findByUserOneIdAndUserTwoId(normalizedPair[0], normalizedPair[1])
				.orElseThrow(() -> new FriendshipNotFoundException(
						HttpResponseMessage.HTTP_FRIENDSHIP_NOT_FOUND_RESPONSE_MESSAGE
								.getMessage()));

		friendshipRepository.delete(friendship);
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
		simpMessagingTemplate.convertAndSendToUser(username, "/queue/errors", messageResponse);
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
				.receiverId(friendRequest.getReceiver().getId())
				.receiverUsername(friendRequest.getReceiver().getUsername())
				.status(friendRequest.getStatus()).createdAt(friendRequest.getCreatedAt())
				.updatedAt(friendRequest.getUpdatedAt()).build();
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

