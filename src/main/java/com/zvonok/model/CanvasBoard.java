package com.zvonok.model;

import com.zvonok.model.enumeration.CanvasBackground;
import com.zvonok.model.enumeration.CanvasBoardMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "canvas_board", indexes = {
		@Index(name = "idx_canvas_board_call_active", columnList = "call_session_id,active"),
		@Index(name = "idx_canvas_board_call_mode_active",
				columnList = "call_session_id,mode,active")
})
@Getter
@Setter
@NoArgsConstructor
public class CanvasBoard {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "call_session_id", nullable = false)
	private CallSession callSession;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Enumerated(EnumType.STRING)
	@Column(name = "mode", nullable = false, length = 32)
	private CanvasBoardMode mode;

	@Enumerated(EnumType.STRING)
	@Column(name = "background", nullable = false, length = 32)
	private CanvasBackground background;

	@Column(name = "created_by", nullable = false, length = 128)
	private String createdBy;

	@Column(name = "active", nullable = false)
	private boolean active;

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
		updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}
}
