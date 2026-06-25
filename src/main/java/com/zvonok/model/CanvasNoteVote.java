package com.zvonok.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "canvas_note_vote", indexes = {
		@Index(name = "idx_canvas_note_vote_board_id", columnList = "board_id"),
		@Index(name = "idx_canvas_note_vote_note_id", columnList = "note_id")
}, uniqueConstraints = {
		@UniqueConstraint(name = "uk_canvas_note_vote_note_user",
				columnNames = {"note_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class CanvasNoteVote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private CanvasBoard board;

	@ManyToOne(optional = false)
	@JoinColumn(name = "note_id", nullable = false)
	private CanvasStickyNote note;

	@Column(name = "user_id", nullable = false, length = 255)
	private String userId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
