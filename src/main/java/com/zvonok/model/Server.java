package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonManagedReference;
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
@Table(name = "server")
public class Server {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(nullable = false, unique = true, length = 15)
	private String invitedCode;

	@Column(nullable = false)
	private Integer maxMember = 10000;

	private Boolean isActive = true;

	private LocalDateTime createdAt;

	@ManyToOne
	@JoinColumn(name = "owner_id", nullable = false)
	private User owner;

	@OneToMany(mappedBy = "server", cascade = {CascadeType.REMOVE, CascadeType.PERSIST},
			orphanRemoval = true)
	@JsonIgnore
	private List<ServerMember> members;

	@OneToMany(mappedBy = "server", cascade = {CascadeType.REMOVE, CascadeType.PERSIST},
			orphanRemoval = true)
	@JsonManagedReference
	private List<ChannelFolder> channelFolders;

	@OneToMany(mappedBy = "server", cascade = {CascadeType.REMOVE, CascadeType.PERSIST},
			orphanRemoval = true)
	@JsonIgnore
	private List<ServerRole> roles;
}
