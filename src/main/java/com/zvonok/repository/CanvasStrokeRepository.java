package com.zvonok.repository;

import com.zvonok.model.CanvasStroke;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CanvasStrokeRepository extends JpaRepository<CanvasStroke, Long> {

	long countByBoardId(Long boardId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<CanvasStroke> findByBoardIdAndStrokeKey(Long boardId, String strokeKey);

	boolean existsByBoardIdAndStrokeKey(Long boardId, String strokeKey);

	@EntityGraph(attributePaths = "points")
	List<CanvasStroke> findAllByBoardIdOrderByCreatedAtAsc(Long boardId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CanvasStroke s where s.board.id = :boardId")
	void deleteAllByBoardId(@Param("boardId") Long boardId);
}
