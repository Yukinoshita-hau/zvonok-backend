package com.zvonok.repository;

import com.zvonok.model.CanvasNoteVote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface CanvasNoteVoteRepository extends JpaRepository<CanvasNoteVote, Long> {

	List<CanvasNoteVote> findAllByBoardIdOrderByCreatedAtAscIdAsc(Long boardId);

	Optional<CanvasNoteVote> findByNoteIdAndUserId(Long noteId, String userId);

	boolean existsByNoteIdAndUserId(Long noteId, String userId);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	void deleteAllByNoteId(Long noteId);
}
