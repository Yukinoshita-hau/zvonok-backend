package com.zvonok.service;

import java.util.Optional;
import org.springframework.stereotype.Service;
import com.zvonok.controller.dto.RestoreCallSessionResponse;
import com.zvonok.service.dto.LiveKitTokenResponse;
import com.zvonok.service.enums.CallRestoreType;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CallRestoreService {
    private final LiveKitCallTokenService tokenService;
    private final CallRestoreStateService restoreStateService;

    public Optional<RestoreCallSessionResponse> restoreCallSession(String username, String clientId) {
        Optional<RestoreCallSessionResponse> candidate = restoreStateService.findCandidate(username);
        if (candidate.isEmpty() || candidate.get().getCallRestoreType() != CallRestoreType.ACTIVE_CALL) {
            return candidate;
        }
        // No database transaction or row lock is held across LiveKit REST.
        RestoreCallSessionResponse response = candidate.get();
        LiveKitTokenResponse token = tokenService.issueCallToken(response.getCallId(), username, clientId, false);
        response.setMediaOwnership(token.getMediaOwnership());
        response.setServerUrl(token.getServerUrl());
        response.setParticipantToken(token.getParticipantToken());
        response.setExpiresAt(token.getExpiresAt());
        return Optional.of(response);
    }
}
