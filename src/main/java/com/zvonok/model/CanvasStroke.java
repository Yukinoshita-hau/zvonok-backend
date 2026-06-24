package com.zvonok.model;

import com.zvonok.model.enumeration.CanvasTool;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "canvas_stroke", indexes = {
		@Index(name = "idx_canvas_stroke_board_created", columnList = "board_id,created_at")
}, uniqueConstraints = {
		@UniqueConstraint(name = "uk_canvas_stroke_board_key",
				columnNames = {"board_id", "stroke_key"})
})
@Getter
@Setter
@NoArgsConstructor
public class CanvasStroke {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "board_id", nullable = false)
	private CanvasBoard board;

	@Column(name = "stroke_key", nullable = false, length = 128)
	private String strokeKey;

	@Column(name = "user_id", nullable = false, length = 128)
	private String userId;

	@Column(name = "color", nullable = false, length = 64)
	private String color;

	@Column(name = "width", nullable = false)
	private Integer width;

	@Enumerated(EnumType.STRING)
	@Column(name = "tool", nullable = false, length = 32)
	private CanvasTool tool;

	@Column(name = "ended_at")
	private Instant endedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "stroke", cascade = CascadeType.ALL, orphanRemoval = true,
			fetch = FetchType.LAZY)
	@OrderBy("position ASC")
	private List<CanvasPoint> points = new ArrayList<>();

	@PrePersist
	protected void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}
