package com.zvonok.repository;

import com.zvonok.model.CallSession;
import com.zvonok.model.enumeration.CallSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Collection;
import java.util.Optional;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CallSession> findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId,
            Collection<CallSessionStatus> statuses);

    boolean existsByRoomIdAndStatusIn(Long roomId, Collection<CallSessionStatus> statuses);

    Optional<CallSession> findByLivekitRoomName(String livekitRoomName);
}
