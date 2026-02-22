package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.model.enumeration.UserStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "user")
public class User {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(unique = true, nullable = false, length = 50)
	private String username;

	@Column(unique = true, nullable = false, length = 100)
	private String email;

	@Column(nullable = false, name = "is_email_verified")
	private Boolean isEmailVerified = false;

	@Column(nullable = false, length = 100)
	@JsonIgnore
	private String password;

	@Enumerated(EnumType.STRING)
	private UserStatus status = UserStatus.OFFLINE;

	private LocalDateTime lastSeenAt;

	private String avatarUrl;

	@UpdateTimestamp
	@Column(name = "updated_at")
	private LocalDateTime updatedAt;

	@CreationTimestamp
	private LocalDateTime createdAt;

	@OneToMany(mappedBy = "user")
	@JsonIgnore
	private List<ServerMember> serverMemberships;

	@OneToMany(mappedBy = "sender")
	@JsonBackReference
	private List<Message> sentMessage;

	@OneToMany(mappedBy = "sender")
	@JsonIgnore
	private List<FriendRequest> sentFriendRequests;

	@OneToMany(mappedBy = "receiver")
	@JsonIgnore
	private List<FriendRequest> receivedFriendRequests;

	@OneToMany(mappedBy = "userOne")
	@JsonIgnore
	private List<Friendship> friendshipsInitiated;

	@OneToMany(mappedBy = "userTwo")
	@JsonIgnore
	private List<Friendship> friendshipsReceived;

	@OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
	@JsonIgnore
	private List<RefreshToken> refreshTokens;
}
