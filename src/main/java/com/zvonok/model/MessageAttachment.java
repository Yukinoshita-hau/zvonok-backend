package com.zvonok.model;

import com.zvonok.model.enumeration.AttachmentType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "message_attachment")
public class MessageAttachment {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "message_id", nullable = false)
	private Message message;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private AttachmentType type;

	@Column(nullable = false, length = 512)
	private String storageKey;

	@Column(nullable = false, length = 512)
	private String originalFileName;

	@Column(nullable = false, length = 128)
	private String contentType;

	@Column(nullable = false)
	private Long sizeBytes;

	private Integer width;

	private Integer height;

	private Long durationMs;

	@Column(columnDefinition = "text")
	private String waveformJson;

	@Column(nullable = false)
	private LocalDateTime createdAt;

	@PrePersist
	private void prePersist() {
		if (createdAt == null) {
			createdAt = LocalDateTime.now();
		}
	}
}
