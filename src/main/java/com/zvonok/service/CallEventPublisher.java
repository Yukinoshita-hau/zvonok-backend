package com.zvonok.service;

import com.zvonok.model.User;
import com.zvonok.service.dto.BaseCallEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
@RequiredArgsConstructor
public class CallEventPublisher {

    private static final String CALL_QUEUE_PATH = "/queue/call";

    private final SimpMessagingTemplate messagingTemplate;

    public void sendToUser(String username, BaseCallEvent event) {
        messagingTemplate.convertAndSendToUser(username, CALL_QUEUE_PATH, event);
    }

    public void sendToUsers(Collection<User> users, BaseCallEvent event) {
        users.forEach(u -> sendToUser(u.getUsername(), event));
    }
}
