package com.zvonok.repository;

import com.zvonok.model.CallParticipant;
import com.zvonok.model.enumeration.CallParticipantStatus;
import com.zvonok.model.enumeration.CallSessionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CallParticipantRepository extends JpaRepository<CallParticipant, Long> {

	Optional<CallParticipant> findByCallSessionIdAndUserId(Long callSessionId, Long userId);

	List<CallParticipant> findAllByCallSessionId(Long callSessionId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update CallParticipant p
		set p.lastSeenAt = :seenAt
		where p.callSession.id = :callId
			and p.user.username = :username
			and p.callSession.status in :allowedCallStatuses
			and p.status in :allowedParticipantStatuses
	""")
	int touchLastSeenIfTokenAllowed(
		@Param("callId") Long callId,
		@Param("username") String username,
		@Param("seenAt") LocalDateTime seenAt,
		@Param("allowedCallStatuses") Collection<CallSessionStatus> allowedCallStatuses,
		@Param("allowedParticipantStatuses") Collection<CallParticipantStatus> allowedParticipantStatuses
	);

	long countByCallSessionIdAndStatusIn(Long callSessionId,
			Collection<CallParticipantStatus> statuses);
}
