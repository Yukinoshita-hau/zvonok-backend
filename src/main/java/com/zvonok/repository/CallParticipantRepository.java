package com.zvonok.repository;

import com.zvonok.model.CallParticipant;
import com.zvonok.model.enumeration.CallParticipantStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CallParticipantRepository extends JpaRepository<CallParticipant, Long> {

    Optional<CallParticipant> findByCallSessionIdAndUserId(Long callSessionId, Long userId);

    List<CallParticipant> findAllByCallSessionId(Long callSessionId);

    long countByCallSessionIdAndStatusIn(Long callSessionId, Collection<CallParticipantStatus> statuses);
}
