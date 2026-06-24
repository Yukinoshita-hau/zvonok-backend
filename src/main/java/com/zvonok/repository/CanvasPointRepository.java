package com.zvonok.repository;

import com.zvonok.model.CanvasPoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CanvasPointRepository extends JpaRepository<CanvasPoint, Long> {

	long countByStrokeId(Long strokeId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from CanvasPoint p where p.stroke.board.id = :boardId")
	void deleteAllByBoardId(@Param("boardId") Long boardId);
}
