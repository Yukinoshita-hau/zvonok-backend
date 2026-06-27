package com.zvonok.model;

import com.zvonok.service.dto.code.ExecutionResponseStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
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
@Table(name = "code_session", indexes = {
		@Index(name = "idx_code_session_call_active", columnList = "call_session_id,active")
})
@Getter
@Setter
@NoArgsConstructor
public class CodeSession {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false, fetch = FetchType.LAZY)
	@JoinColumn(name = "call_session_id", nullable = false)
	private CallSession callSession;

	@Column(name = "room_id", nullable = false)
	private Long roomId;

	@Column(name = "created_by", nullable = false, length = 128)
	private String createdBy;

	@Column(name = "active", nullable = false)
	private boolean active;

	@Column(name = "language", nullable = false, length = 32)
	private String language;

	@Column(name = "code", nullable = false, length = 50_000)
	private String code;

	@Column(name = "stdin", nullable = false, length = 20_000)
	private String stdin;

	@Column(name = "last_output", length = 50_000)
	private String lastOutput;

	@Enumerated(EnumType.STRING)
	@Column(name = "last_status", length = 32)
	private ExecutionResponseStatus lastStatus;

	@Column(name = "last_exit_code")
	private Integer lastExitCode;

	@Column(name = "last_execution_time_ms")
	private Long lastExecutionTimeMs;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "active_editor_user_id")
	private User activeEditor;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Column(name = "closed_at")
	private Instant closedAt;

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
