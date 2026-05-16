package com.zvonok.model;

import java.time.LocalDateTime;
import com.zvonok.model.enumeration.CallRecordingStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "call_recording", indexes = {
	@Index(name = "idx_call_recording_call_status", columnList = "call_session_id,status"),
	@Index(name = "idx_call_recording_egress_id", columnList = "egress_id")
})
@Getter
@Setter
@NoArgsConstructor
public class CallRecording {
	
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "started_by_user_id", nullable = false)
	private User startedBy;

	@ManyToOne(optional = false)
	@JoinColumn(name = "call_session_id", nullable = false)
	private CallSession callSession;

	@Column(name = "egress_id", nullable = false, length = 128)
	private String egressId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 32)
	private CallRecordingStatus status;

	@Column(name = "file_path", nullable = false, length = 512)
	private String filePath;

	@Column(name = "file_location", length = 1024)
	private String fileLocation;

	@Column(name = "started_at", nullable = false)
	private LocalDateTime startedAt;

	@Column(name = "ended_at")
	private LocalDateTime endedAt;

	@Column(name = "error_message", length = 1024)
	private String errorMessage;

	@Column(name = "created_at", nullable = false)
	private LocalDateTime createdAt;

	@Column(name = "updated_at", nullable = false)
	private LocalDateTime updatedAt;

	@PrePersist
	protected void onCreate() {
		LocalDateTime now = LocalDateTime.now();
		createdAt = now;
		updatedAt = now;
		if (startedAt == null) {
			startedAt = now;
		}
	}

	@PreUpdate
	protected void onUpdate() {
		updatedAt = LocalDateTime.now();
	}
}
