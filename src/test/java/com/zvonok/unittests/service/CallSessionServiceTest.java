package com.zvonok.unittests.service;

import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.DeclineCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.controller.enums.CallInviteType;
import com.zvonok.model.CallParticipant;
import com.zvonok.model.CallSession;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.CallParticipantRole;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import com.zvonok.service.CallEventPublisher;
import com.zvonok.service.CallSessionService;
import com.zvonok.service.RoomService;
import com.zvonok.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CallSessionServiceTest {

    @Mock
    private RoomService roomService;
    @Mock
    private UserService userService;
    @Mock
    private CallSessionRepository callSessionRepository;
    @Mock
    private CallParticipantRepository callParticipantRepository;
    @Mock
    private CallEventPublisher callEventPublisher;

    @InjectMocks
    private CallSessionService callSessionService;

    private Room privateRoom;
    private User caller;
    private User receiver;

    @BeforeEach
    void setUp() {
        caller = new User();
        caller.setId(1L);
        caller.setUsername("caller");
        caller.setDisplayName("Caller");

        receiver = new User();
        receiver.setId(2L);
        receiver.setUsername("receiver");
        receiver.setDisplayName("Receiver");

        privateRoom = new Room();
        privateRoom.setId(11L);
        privateRoom.setType(RoomType.PRIVATE);
        privateRoom.setMembers(List.of(caller, receiver));
    }

    @Test
    void startPrivateCall_shouldCreateRingingSession() {
        InviteCallDto dto = new InviteCallDto();
        dto.setChatRoomId(privateRoom.getId());
        dto.setCallType(CallInviteType.audio);

        when(roomService.getRoom(privateRoom.getId(), caller.getUsername())).thenReturn(privateRoom);
        when(userService.getUser(caller.getUsername())).thenReturn(caller);
        when(callSessionRepository.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(eq(privateRoom.getId()),
                ArgumentMatchers.<Set<CallSessionStatus>>any())).thenReturn(Optional.empty());
        when(callSessionRepository.save(any(CallSession.class))).thenAnswer(invocation -> {
            CallSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(100L);
            }
            return session;
        });
        when(callParticipantRepository.save(any(CallParticipant.class))).thenAnswer(i -> i.getArgument(0));
        when(callParticipantRepository.findAllByCallSessionId(100L)).thenReturn(List.of(
                participant(100L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(100L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.RINGING)
        ));

        CallSession session = callSessionService.startCall(caller.getUsername(), dto);

        assertEquals(CallSessionStatus.RINGING, session.getStatus());
        assertEquals("dm-11-call-100", session.getLivekitRoomName());
        verify(callEventPublisher, times(1)).sendToUser(eq(receiver.getUsername()), any());
    }

    @Test
    void acceptPrivateCall_shouldActivateSession() {
        AcceptCallDto dto = new AcceptCallDto();
        dto.setCallId(200L);

        CallSession session = new CallSession();
        session.setId(200L);
        session.setRoom(privateRoom);
        session.setRoomType(RoomType.PRIVATE);
        session.setStatus(CallSessionStatus.RINGING);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("dm-11");

        when(userService.getUser(receiver.getUsername())).thenReturn(receiver);
        when(callSessionRepository.findById(200L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(privateRoom.getId(), receiver.getUsername())).thenReturn(privateRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(200L, receiver.getId()))
                .thenReturn(Optional.of(participant(200L, receiver, CallParticipantRole.MEMBER,
                        CallParticipantStatus.RINGING)));
        when(callParticipantRepository.findAllByCallSessionId(200L)).thenReturn(List.of(
                participant(200L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(200L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.ACCEPTED)
        ));

        CallSession updated = callSessionService.accept(receiver.getUsername(), dto);

        assertEquals(CallSessionStatus.ACTIVE, updated.getStatus());
        verify(callEventPublisher, atLeastOnce()).sendToUser(eq(caller.getUsername()), any());
    }

    @Test
    void declineGroupCall_shouldNotEndSession() {
        Room groupRoom = new Room();
        groupRoom.setId(55L);
        groupRoom.setType(RoomType.GROUP);
        groupRoom.setMembers(List.of(caller, receiver));

        DeclineCallDto dto = new DeclineCallDto();
        dto.setCallId(300L);

        CallSession session = new CallSession();
        session.setId(300L);
        session.setRoom(groupRoom);
        session.setRoomType(RoomType.GROUP);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("group-55");

        when(userService.getUser(receiver.getUsername())).thenReturn(receiver);
        when(callSessionRepository.findById(300L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(groupRoom.getId(), receiver.getUsername())).thenReturn(groupRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(300L, receiver.getId()))
                .thenReturn(Optional.of(participant(300L, receiver, CallParticipantRole.MEMBER,
                        CallParticipantStatus.INVITED)));

        CallSession updated = callSessionService.decline(receiver.getUsername(), dto);

        assertEquals(CallSessionStatus.ACTIVE, updated.getStatus());
        verify(callEventPublisher).sendToUser(eq(caller.getUsername()), any());
    }


    @Test
    void startPrivateCall_shouldCreateUniqueRoomNamePerSessionEvenIfEndedExists() {
        InviteCallDto dto = new InviteCallDto();
        dto.setChatRoomId(privateRoom.getId());
        dto.setCallType(CallInviteType.audio);

        when(roomService.getRoom(privateRoom.getId(), caller.getUsername())).thenReturn(privateRoom);
        when(userService.getUser(caller.getUsername())).thenReturn(caller);
        when(callSessionRepository.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(eq(privateRoom.getId()),
                ArgumentMatchers.<Set<CallSessionStatus>>any())).thenReturn(Optional.empty());

        when(callSessionRepository.save(any(CallSession.class))).thenAnswer(invocation -> {
            CallSession session = invocation.getArgument(0);
            if (session.getId() == null) {
                session.setId(101L);
            }
            return session;
        });

        when(callParticipantRepository.save(any(CallParticipant.class))).thenAnswer(i -> i.getArgument(0));
        when(callParticipantRepository.findAllByCallSessionId(101L)).thenReturn(List.of(
                participant(101L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(101L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.RINGING)
        ));

        CallSession session = callSessionService.startCall(caller.getUsername(), dto);

        assertEquals("dm-11-call-101", session.getLivekitRoomName());
    }

    @Test
    void startPrivateCall_shouldReuseExistingActiveSession() {
        InviteCallDto dto = new InviteCallDto();
        dto.setChatRoomId(privateRoom.getId());
        dto.setCallType(CallInviteType.audio);

        CallSession existing = new CallSession();
        existing.setId(500L);
        existing.setRoom(privateRoom);
        existing.setRoomType(RoomType.PRIVATE);
        existing.setStatus(CallSessionStatus.RINGING);
        existing.setHostUser(caller);
        existing.setCreatedBy(caller);
        existing.setLivekitRoomName("dm-11-call-500");

        when(roomService.getRoom(privateRoom.getId(), caller.getUsername())).thenReturn(privateRoom);
        when(userService.getUser(caller.getUsername())).thenReturn(caller);
        when(callSessionRepository.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(eq(privateRoom.getId()),
                ArgumentMatchers.<Set<CallSessionStatus>>any())).thenReturn(Optional.of(existing));
        when(callParticipantRepository.findAllByCallSessionId(500L)).thenReturn(List.of(
                participant(500L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(500L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.RINGING)
        ));

        CallSession session = callSessionService.startCall(caller.getUsername(), dto);

        assertEquals(500L, session.getId());
        verify(callSessionRepository, never()).save(any(CallSession.class));
    }
    private CallParticipant participant(Long callId, User user, CallParticipantRole role,
            CallParticipantStatus status) {
        CallSession session = new CallSession();
        session.setId(callId);

        CallParticipant participant = new CallParticipant();
        participant.setCallSession(session);
        participant.setUser(user);
        participant.setRole(role);
        participant.setStatus(status);
        return participant;
    }
}
