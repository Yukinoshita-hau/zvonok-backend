package com.zvonok.service;

import com.zvonok.exception.CallStateConflictException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.model.CallParticipant;
import com.zvonok.repository.CallParticipantRepository;
import com.zvonok.repository.CallSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.function.Supplier;
import com.zvonok.service.dto.LiveKitTokenResponse;

@Service
@RequiredArgsConstructor
@Transactional
public class CallMediaOwnershipService {
    private final CallSessionRepository calls;
    private final CallParticipantRepository participants;

    @Value("${livekit.token-ttl-minutes:10}")
    private long tokenTtlMinutes = 10;

    // Self-hosted LiveKit refreshes reconnect tokens for at least ten minutes.
    private LocalDateTime expiry() { return LocalDateTime.now().plusMinutes(Math.max(10, tokenTtlMinutes) + 1); }

    private CallParticipant locked(Long callId, String username) {
        calls.findByIdForUpdate(callId).orElseThrow(() -> new CallStateConflictException("Call not found"));
        return participants.findByCallSessionIdAndUserUsername(callId, username)
                .orElseThrow(() -> new InsufficientPermissionsException("Call participant required"));
    }

    public boolean claim(Long callId, String username, String clientId, boolean transfer) {
        CallParticipant p = locked(callId, username);
        boolean expired = p.getMediaLeaseUntil() != null && p.getMediaLeaseUntil().isBefore(LocalDateTime.now());
        if (Objects.equals(p.getMediaClientId(), clientId)) {
            if (expired || p.getMediaTransferClientId() != null) return false;
            p.setMediaLeaseUntil(expiry());
            return true;
        }
        if (p.getMediaClientId() == null || expired) {
            if (!expired && p.getMediaTransferClientId() != null && !clientId.equals(p.getMediaTransferClientId())) return false;
            p.setMediaClientId(clientId);
            p.setMediaLeaseUntil(expiry());
            p.setMediaTransferClientId(null);
            return true;
        }
        if (transfer && p.getMediaTransferClientId() == null) p.setMediaTransferClientId(clientId);
        return false;
    }

    public LiveKitTokenResponse issue(Long callId, String username, String clientId, boolean transfer,
            boolean present, Supplier<LiveKitTokenResponse> signer) {
        CallParticipant p = locked(callId, username);
        boolean expired = p.getMediaLeaseUntil() != null && p.getMediaLeaseUntil().isBefore(LocalDateTime.now());
        boolean unowned = p.getMediaClientId() == null || expired;
        LiveKitTokenResponse response;
        if ((!Objects.equals(clientId, p.getMediaClientId()) && unowned && present)
                || !claim(callId, username, clientId, transfer)) {
            response = new LiveKitTokenResponse();
            response.setCallId(callId);
            response.setMediaOwnership("PASSIVE");
        } else {
            response = signer.get();
            response.setMediaOwnership("OWNED");
        }
        return response;
    }

    public String renew(Long callId, String username, String clientId) {
        CallParticipant p = locked(callId, username);
        if (!Objects.equals(clientId, p.getMediaClientId()) || p.getMediaTransferClientId() != null
                || p.getMediaLeaseUntil() == null || p.getMediaLeaseUntil().isBefore(LocalDateTime.now())) return "RELEASE";
        p.setMediaLeaseUntil(expiry());
        return "OWNED";
    }

    public void release(Long callId, String username, String clientId) {
        CallParticipant p = locked(callId, username);
        if (!Objects.equals(clientId, p.getMediaClientId())) return;
        // Keep the handoff reservation; issuance also waits for SFU absence.
        p.setMediaClientId(null);
        p.setMediaLeaseUntil(p.getMediaTransferClientId() == null ? null : expiry());
    }
}
