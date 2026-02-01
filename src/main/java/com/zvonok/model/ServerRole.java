package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.service.dto.Permission;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "server_role")
public class ServerRole {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String name;

	@Column(length = 7)
	private String color = "#ffffff";

	@Column(nullable = false)
	private Integer position = 0; // Иерархия ролей

	@Column(nullable = false)
	private Long serverPermissions = Permission.NOTHING.getValue();

	private Boolean mentionable = true;
	private Boolean isEveryone = false; // Роль @everyone
	private Boolean isActive = true;
	private LocalDateTime createdAt;

	@ManyToOne
	@JsonBackReference
	@JoinColumn(name = "server_id", nullable = false)
	private Server server;

	@OneToMany(mappedBy = "role",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<ServerMemberRole> memberRoles;

	@OneToMany(mappedBy = "role",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<FolderPermissionOverride> folderOverrides;

	@OneToMany(mappedBy = "role",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<ChannelPermissionOverride> channelOverrides;
}
