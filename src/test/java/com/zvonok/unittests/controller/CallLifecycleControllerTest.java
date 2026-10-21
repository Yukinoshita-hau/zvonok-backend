package com.zvonok.unittests.controller;

import com.zvonok.controller.CallLifecycleController;
import com.zvonok.controller.dto.EndCallDto;
import com.zvonok.security.dto.UserPrincipal;
import com.zvonok.service.CallService;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CallLifecycleControllerTest {

    @Test
    void endsCallForAuthenticatedPrincipal() {
        CallService callService = mock(CallService.class);
        CallLifecycleController controller = new CallLifecycleController(callService);
        EndCallDto dto = new EndCallDto();
        dto.setCallId(103L);
        dto.setChatRoomId(44L);

        assertEquals(HttpStatus.NO_CONTENT, controller.endCall(dto,
                new UserPrincipal("diagnostic-owner", "test-token")).getStatusCode());
        verify(callService).callEnd("diagnostic-owner", dto);
    }
}
