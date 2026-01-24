package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
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
@Table(name = "channel_folder")
public class ChannelFolder {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, length = 100)
	private String name;

	@ManyToOne
	@JoinColumn(name = "server_id", nullable = false)
	@JsonBackReference
	private Server server;

	@OneToMany(mappedBy = "folder",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonManagedReference
	private List<Channel> channels;

	@OneToMany(mappedBy = "folder",
			cascade = {CascadeType.REMOVE, CascadeType.PERSIST, CascadeType.MERGE})
	@JsonIgnore
	private List<FolderPermissionOverride> permissionOverrides;

	private Integer position = 0; // Порядок отображения папок
	private Boolean collapsed = false; // Свернута ли папка по умолчанию
	private Boolean isActive = true;
	private LocalDateTime createdAt;
}
