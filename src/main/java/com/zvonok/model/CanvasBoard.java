package com.zvonok.model;

import com.zvonok.model.enumeration.CanvasBackground;
import com.zvonok.model.enumeration.CanvasBoardMode;
import com.zvonok.model.enumeration.CanvasDrawingAccess;
import com.zvonok.model.enumeration.CanvasTemplateType;
import com.zvonok.model.enumeration.CanvasTimerStatus;
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

	@Column(name = "overlay_owner_username", length = 255)
	private String overlayOwnerUsername;

	@Column(name = "created_by", nullable = false, length = 128)
	private String createdBy;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Enumerated(EnumType.STRING)
	@Column(name = "drawing_access", nullable = false, length = 32)
	private CanvasDrawingAccess drawingAccess = CanvasDrawingAccess.EVERYONE;

	@Column(name = "selected_drawer_username", length = 255)
	private String selectedDrawerUsername;

	@Enumerated(EnumType.STRING)
	@Column(name = "template_type", nullable = false, length = 64)
	private CanvasTemplateType templateType = CanvasTemplateType.CLEAN;

	@Column(name = "timer_started_at")
	private Instant timerStartedAt;

	@Column(name = "timer_duration_seconds")
	private Integer timerDurationSeconds;

	@Enumerated(EnumType.STRING)
	@Column(name = "timer_status", nullable = false, length = 32)
	private CanvasTimerStatus timerStatus = CanvasTimerStatus.STOPPED;

	@Column(name = "background_image_url", length = 1024)
	private String backgroundImageUrl;

	@Column(name = "background_image_created_by", length = 255)
	private String backgroundImageCreatedBy;

	@Column(name = "background_image_created_at")
	private Instant backgroundImageCreatedAt;

	@Column(name = "presenter_username", length = 255)
	private String presenterUsername;

	@Column(name = "presenter_mode_enabled", nullable = false)
	private boolean presenterModeEnabled = false;

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
		if (drawingAccess == null) {
			drawingAccess = CanvasDrawingAccess.EVERYONE;
		}
		if (templateType == null) {
			templateType = CanvasTemplateType.CLEAN;
		}
		if (timerStatus == null) {
			timerStatus = CanvasTimerStatus.STOPPED;
		}
		updatedAt = now;
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = Instant.now();
	}
}
