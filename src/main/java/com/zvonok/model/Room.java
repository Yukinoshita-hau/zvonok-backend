package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.zvonok.model.enumeration.RoomType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "room")
public class Room {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 100)
	private String name;

	@Enumerated(EnumType.STRING)
	private RoomType type = RoomType.PRIVATE;

	private String avatarUrl;

	private Boolean isActive = true;

	private LocalDateTime createdAt;

	private Long lastMessageId;
	private String lastMessageContent;
	private LocalDateTime lastActivityAt;


	@ManyToMany
	@JoinTable(name = "room_members", joinColumns = @JoinColumn(name = "room_id"),
			inverseJoinColumns = @JoinColumn(name = "user_id"))
	@JsonIgnoreProperties({"rooms", "friends", "password", "email", "isEmailVerified"})
	private List<User> members;

	@PrePersist
	protected void onCreate() {
		this.createdAt = LocalDateTime.now();
		this.lastActivityAt = LocalDateTime.now();
	}
}
