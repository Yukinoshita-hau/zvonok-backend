package com.zvonok.unittests.service;

import com.zvonok.controller.dto.AcceptCallDto;
import com.zvonok.controller.dto.DeclineCallDto;
import com.zvonok.controller.dto.InviteCallDto;
import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.controller.dto.LeaveCallDto;
import com.zvonok.controller.enums.CallInviteType;
import com.zvonok.exception.InsufficientPermissionsException;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    void groupNonHostLeave_shouldMarkLeftAndKeepActiveWhenOthersRemain() {
        LeaveCallDto dto = new LeaveCallDto();
        dto.setCallId(700L);

        Room groupRoom = new Room();
        groupRoom.setId(70L);
        groupRoom.setType(RoomType.GROUP);
        groupRoom.setMembers(List.of(caller, receiver));

        CallSession session = new CallSession();
        session.setId(700L);
        session.setRoom(groupRoom);
        session.setRoomType(RoomType.GROUP);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("group-70-call-700");

        when(userService.getUser(receiver.getUsername())).thenReturn(receiver);
        when(callSessionRepository.findById(700L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(groupRoom.getId(), receiver.getUsername())).thenReturn(groupRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(700L, receiver.getId()))
                .thenReturn(Optional.of(participant(700L, receiver, CallParticipantRole.MEMBER,
                        CallParticipantStatus.ACCEPTED)));
        when(callParticipantRepository.countByCallSessionIdAndStatusIn(eq(700L), any()))
                .thenReturn(1L);
        when(callParticipantRepository.findAllByCallSessionId(700L)).thenReturn(List.of(
                participant(700L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(700L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.LEFT)
        ));

        CallSession updated = callSessionService.leave(receiver.getUsername(), dto);

        assertEquals(CallSessionStatus.ACTIVE, updated.getStatus());
        verify(callEventPublisher, atLeastOnce()).sendToUser(eq(caller.getUsername()), any());
    }

    @Test
    void groupLastActiveLeave_shouldEndSession() {
        LeaveCallDto dto = new LeaveCallDto();
        dto.setCallId(701L);

        Room groupRoom = new Room();
        groupRoom.setId(71L);
        groupRoom.setType(RoomType.GROUP);
        groupRoom.setMembers(List.of(caller));

        CallSession session = new CallSession();
        session.setId(701L);
        session.setRoom(groupRoom);
        session.setRoomType(RoomType.GROUP);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("group-71-call-701");

        when(userService.getUser(caller.getUsername())).thenReturn(caller);
        when(callSessionRepository.findById(701L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(groupRoom.getId(), caller.getUsername())).thenReturn(groupRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(701L, caller.getId()))
                .thenReturn(Optional.of(participant(701L, caller, CallParticipantRole.HOST,
                        CallParticipantStatus.ACCEPTED)));
        when(callParticipantRepository.countByCallSessionIdAndStatusIn(eq(701L), any()))
                .thenReturn(0L);
        when(callParticipantRepository.findAllByCallSessionId(701L)).thenReturn(List.of(
                participant(701L, caller, CallParticipantRole.HOST, CallParticipantStatus.LEFT)
        ));

        CallSession updated = callSessionService.leave(caller.getUsername(), dto);

        assertEquals(CallSessionStatus.ENDED, updated.getStatus());
        assertEquals("NO_ACTIVE_PARTICIPANTS", updated.getEndReason());
    }

    @Test
    void privateLeave_shouldEndSession() {
        LeaveCallDto dto = new LeaveCallDto();
        dto.setCallId(702L);

        CallSession session = new CallSession();
        session.setId(702L);
        session.setRoom(privateRoom);
        session.setRoomType(RoomType.PRIVATE);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("dm-11-call-702");

        when(userService.getUser(receiver.getUsername())).thenReturn(receiver);
        when(callSessionRepository.findById(702L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(privateRoom.getId(), receiver.getUsername())).thenReturn(privateRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(702L, receiver.getId()))
                .thenReturn(Optional.of(participant(702L, receiver, CallParticipantRole.MEMBER,
                        CallParticipantStatus.ACCEPTED)));
        when(callParticipantRepository.findAllByCallSessionId(702L)).thenReturn(List.of(
                participant(702L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(702L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.LEFT)
        ));

        CallSession updated = callSessionService.leave(receiver.getUsername(), dto);

        assertEquals(CallSessionStatus.ENDED, updated.getStatus());
        assertEquals("PRIVATE_PARTICIPANT_LEFT", updated.getEndReason());
    }

    @Test
    void groupNonHostEnd_shouldThrowAndKeepSession() {
        EndCallDto dto = new EndCallDto();
        dto.setCallId(703L);

        Room groupRoom = new Room();
        groupRoom.setId(73L);
        groupRoom.setType(RoomType.GROUP);
        groupRoom.setMembers(List.of(caller, receiver));

        CallSession session = new CallSession();
        session.setId(703L);
        session.setRoom(groupRoom);
        session.setRoomType(RoomType.GROUP);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("group-73-call-703");

        when(userService.getUser(receiver.getUsername())).thenReturn(receiver);
        when(callSessionRepository.findById(703L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(groupRoom.getId(), receiver.getUsername())).thenReturn(groupRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(703L, receiver.getId()))
                .thenReturn(Optional.of(participant(703L, receiver, CallParticipantRole.MEMBER,
                        CallParticipantStatus.ACCEPTED)));

        assertThrows(InsufficientPermissionsException.class,
                () -> callSessionService.end(receiver.getUsername(), dto));
        assertEquals(CallSessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    void groupHostEnd_shouldEndSession() {
        EndCallDto dto = new EndCallDto();
        dto.setCallId(704L);

        Room groupRoom = new Room();
        groupRoom.setId(74L);
        groupRoom.setType(RoomType.GROUP);
        groupRoom.setMembers(List.of(caller, receiver));

        CallSession session = new CallSession();
        session.setId(704L);
        session.setRoom(groupRoom);
        session.setRoomType(RoomType.GROUP);
        session.setStatus(CallSessionStatus.ACTIVE);
        session.setHostUser(caller);
        session.setCreatedBy(caller);
        session.setLivekitRoomName("group-74-call-704");

        when(userService.getUser(caller.getUsername())).thenReturn(caller);
        when(callSessionRepository.findById(704L)).thenReturn(Optional.of(session));
        when(roomService.getRoom(groupRoom.getId(), caller.getUsername())).thenReturn(groupRoom);
        when(callParticipantRepository.findByCallSessionIdAndUserId(704L, caller.getId()))
                .thenReturn(Optional.of(participant(704L, caller, CallParticipantRole.HOST,
                        CallParticipantStatus.ACCEPTED)));
        when(callParticipantRepository.findAllByCallSessionId(704L)).thenReturn(List.of(
                participant(704L, caller, CallParticipantRole.HOST, CallParticipantStatus.ACCEPTED),
                participant(704L, receiver, CallParticipantRole.MEMBER, CallParticipantStatus.ACCEPTED)
        ));

        CallSession updated = callSessionService.end(caller.getUsername(), dto);
        assertEquals(CallSessionStatus.ENDED, updated.getStatus());
    }

    @Test
    void duplicateStartWithExistingActiveGroup_shouldReturnExistingAndSendOnlyCallStarted() {
        InviteCallDto dto = new InviteCallDto();
        dto.setChatRoomId(80L);
        dto.setCallType(CallInviteType.audio);

        Room groupRoom = new Room();
        groupRoom.setId(80L);
        groupRoom.setType(RoomType.GROUP);
        groupRoom.setMembers(List.of(caller, receiver));

        CallSession existing = new CallSession();
        existing.setId(800L);
        existing.setRoom(groupRoom);
        existing.setRoomType(RoomType.GROUP);
        existing.setStatus(CallSessionStatus.ACTIVE);
        existing.setHostUser(caller);
        existing.setCreatedBy(caller);
        existing.setLivekitRoomName("group-80-call-800");

        when(roomService.getRoom(80L, caller.getUsername())).thenReturn(groupRoom);
        when(userService.getUser(caller.getUsername())).thenReturn(caller);
        when(callSessionRepository.findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(eq(80L),
                ArgumentMatchers.<Set<CallSessionStatus>>any())).thenReturn(Optional.of(existing));

        CallSession session = callSessionService.startCall(caller.getUsername(), dto);

        assertEquals(800L, session.getId());
        verify(callSessionRepository, never()).save(any(CallSession.class));
        verify(callEventPublisher, times(1)).sendToUser(eq(caller.getUsername()), any());
        verify(callEventPublisher, never()).sendToUser(eq(receiver.getUsername()), any());
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
