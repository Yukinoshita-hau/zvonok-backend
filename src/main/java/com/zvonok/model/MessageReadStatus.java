package com.zvonok.model;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "message_read_status", uniqueConstraints = @UniqueConstraint(columnNames = { "message_id", "user_id" }))
public class MessageReadStatus {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "message_id")
	private Message message;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "read_at")
	private LocalDateTime readAt;
}
