package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.model.enumeration.ChannelType;
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
@Table(name = "channel")
public class Channel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "channel_folder_id", nullable = false) @JsonBackReference
    private ChannelFolder folder;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    private ChannelType type = ChannelType.TEXT;

    private Integer userLimit = 100; // лимит участников (для голосовых каналов)
    private Integer slowModeSeconds; // задержка в секундах
    private Boolean isActive = true;
    private LocalDateTime createdAt;

    @OneToMany(mappedBy = "channel")
    @JsonIgnore
    private List<ChannelPermissionOverride> permissionOverrides;

    private Integer position = 0;
    private String topic;
    private Boolean nsfw = false; // контент, который может быть неприемлем в общественных или рабочих условиях
}
