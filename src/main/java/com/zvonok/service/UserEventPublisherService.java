package com.zvonok.service;

import java.util.HashSet;
import java.util.Set;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import com.zvonok.model.User;
import com.zvonok.repository.FriendshipRepository;
import com.zvonok.repository.RoomRepository;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.dto.EventType;
import com.zvonok.service.dto.UserProfileUpdatedEvent;
import com.zvonok.service.dto.response.UserShortResponse;
import com.zvonok.service.enums.BrokerPath;
import com.zvonok.service.enums.UserEventType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserEventPublisherService {

	private final SimpMessagingTemplate messagingTemplate;
	private final UserRepository userRepository;
	private final FriendshipRepository friendshipRepository;
	private final RoomRepository roomRepository;

	public void publishUserProfileUpdated(User updatedUser) {
		Set<Long> recipientIds = new HashSet<>();

		recipientIds.addAll(friendshipRepository.findFriendIdsByUserId(updatedUser.getId()));

		recipientIds.addAll(roomRepository.findUserIdsSharingRoomsWithUser(updatedUser.getId()));

		recipientIds.add(updatedUser.getId());

		Set<String> recipientUsernames = userRepository.findUsernamesByIds(recipientIds);

		UserShortResponse userDto = UserShortResponse.builder().id(updatedUser.getId())
				.username(updatedUser.getUsername()).displayName(updatedUser.getDisplayName())
				.avatarUrl(updatedUser.getAvatarUrl()).aboutMe(updatedUser.getAboutMe()).build();

		UserProfileUpdatedEvent event = UserProfileUpdatedEvent.builder()
				.eventType(UserEventType.USER_PROFILE_UPDATED).user(userDto).build();

		recipientUsernames.stream().forEach(username -> {
			messagingTemplate.convertAndSendToUser(username, BrokerPath.UPDATED_USER_QUEUE_PATH.getPath(), event);
		});
	}
}
