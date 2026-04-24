package com.zvonok.service;

import com.zvonok.model.User;
import com.zvonok.repository.RoomRepository;
import com.zvonok.service.dto.BrokerPath;
import com.zvonok.service.dto.UserShortDto;
import com.zvonok.service.dto.event.UserEvents;
import com.zvonok.service.dto.event.UserEventsType;
import com.zvonok.service.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserEventService {

    private final SimpMessagingTemplate messagingTemplate;
    private final RoomRepository roomRepository;
    private final UserMapper userMapper;

    public void notifyUserProfileUpdated(User user) {
        UserShortDto payload = userMapper.toUserShortDto(user);

        UserEvents event = new UserEvents();
        event.setType(UserEventsType.USER_PROFILE_UPDATED);
        event.setPayload(payload);

        List<String> usernames = roomRepository.findUsernamesWhoShareRoomsWithUser(user.getId());

        usernames.forEach(username -> messagingTemplate.convertAndSendToUser(
                username,
                BrokerPath.USER_EVENTS_QUEUE_PATH.getPath(),
                event
        ));
    }
}
