package com.zvonok.repository;

import com.zvonok.model.CanvasBoard;
import com.zvonok.model.enumeration.CanvasBoardMode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CanvasBoardRepository extends JpaRepository<CanvasBoard, Long> {

	Optional<CanvasBoard> findFirstByCallSessionIdAndModeAndActiveTrue(Long callId,
			CanvasBoardMode mode);

	List<CanvasBoard> findAllByCallSessionIdAndActiveTrueOrderByCreatedAtAsc(Long callId);

	Optional<CanvasBoard> findByIdAndCallSessionId(Long id, Long callId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select b from CanvasBoard b where b.id = :boardId and b.callSession.id = :callId")
	Optional<CanvasBoard> findByIdAndCallSessionIdForUpdate(@Param("boardId") Long boardId,
			@Param("callId") Long callId);
}
