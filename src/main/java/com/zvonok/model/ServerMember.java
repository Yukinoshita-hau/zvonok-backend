package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.service.dto.Permission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "server_member")
public class ServerMember {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne
	@JoinColumn(name = "server_id", nullable = false)
	private Server server;

	@OneToMany(mappedBy = "member",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<ServerMemberRole> memberRoles = new ArrayList<>();

	private Long personalPermissions = Permission.NOTHING.getValue();

	@Column(length = 32)
	private String nickname;

	private Boolean isActive = true;

	private LocalDateTime joinedAt;

	private LocalDateTime leftAt;
}
