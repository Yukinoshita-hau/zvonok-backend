package com.zvonok.repository;

import com.zvonok.model.CallSession;
import com.zvonok.model.enumeration.CallSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<CallSession> findFirstByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId,
            Collection<CallSessionStatus> statuses);

    boolean existsByRoomIdAndStatusIn(Long roomId, Collection<CallSessionStatus> statuses);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CallSession s where s.id = :id")
	Optional<CallSession> findByIdForUpdate(Long id);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("""
		update CallSession s
		set s.livekitRoomReadyAt = :readyAt,
			s.updatedAt = :readyAt
		where s.id = :callId
			and s.status in :allowedStatuses
			and s.livekitRoomReadyAt is null
	""")
	int markLiveKitRoomReadyIfAbsent(
			@Param("callId") Long callId, 
			@Param("readyAt") LocalDateTime readyAt,
			@Param("allowedStatuses") Collection<CallSessionStatus> allowedStatuses
	);

    Optional<CallSession> findByLivekitRoomName(String livekitRoomName);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select s from CallSession s
		where s.room.id = :roomId
			and s.status in :statuses
		order by s.createdAt desc
	""")
	List<CallSession> findAllActiveByRoomIdForUpdate(
		@Param("roomId") Long roomId,
		@Param("statuses") Collection<CallSessionStatus> statuses
	);

	List<CallSession> findAllByRoomIdAndStatusInOrderByCreatedAtDesc(Long roomId, Collection<CallSessionStatus> statuses);

	List<CallSession> findAllByStatusIn(Collection<CallSessionStatus> statuses);
}
