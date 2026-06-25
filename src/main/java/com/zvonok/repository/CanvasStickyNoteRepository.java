package com.zvonok.repository;

import com.zvonok.model.CanvasStickyNote;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CanvasStickyNoteRepository extends JpaRepository<CanvasStickyNote, Long> {

	@Query("""
			select n from CanvasStickyNote n
			where n.board.id = :boardId
			order by n.zIndex asc, n.createdAt asc, n.id asc
			""")
	List<CanvasStickyNote> findAllByBoardIdOrderByZIndexAscCreatedAtAscIdAsc(
			@Param("boardId") Long boardId);

	Optional<CanvasStickyNote> findByIdAndBoardId(Long id, Long boardId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select n from CanvasStickyNote n where n.id = :noteId and n.board.id = :boardId")
	Optional<CanvasStickyNote> findByIdAndBoardIdForUpdate(@Param("noteId") Long noteId,
			@Param("boardId") Long boardId);

	boolean existsByBoardIdAndNoteKey(Long boardId, String noteKey);
}
