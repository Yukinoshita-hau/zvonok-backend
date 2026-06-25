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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "canvas_sticky_note", indexes = {
		@Index(name = "idx_canvas_sticky_note_board_id", columnList = "board_id")
}, uniqueConstraints = {
		@UniqueConstraint(name = "uk_canvas_sticky_note_board_key",
				columnNames = {"board_id", "note_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class CanvasStickyNote {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private CanvasBoard board;

	@Column(name = "note_key", nullable = false, length = 128)
	private String noteKey;

	@Column(name = "created_by", nullable = false, length = 255)
	private String createdBy;

	@Column(name = "text", nullable = false, length = 1000)
	private String text;

	@Column(name = "color", nullable = false, length = 64)
	private String color;

	@Column(name = "x", nullable = false)
	private Double x;

	@Column(name = "y", nullable = false)
	private Double y;

	@Column(name = "width", nullable = false)
	private Double width;

	@Column(name = "height", nullable = false)
	private Double height;

	@Column(name = "z_index", nullable = false)
	private Integer zIndex = 0;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@PrePersist
	protected void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		if (zIndex == null) {
			zIndex = 0;
		}
		updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}
}
