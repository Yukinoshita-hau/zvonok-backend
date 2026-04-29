package com.zvonok.model;

import com.zvonok.model.enumeration.CallParticipantRole;
import com.zvonok.model.enumeration.CallParticipantStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "call_participant", indexes = {
        @Index(name = "idx_call_participant_call_status", columnList = "call_session_id,status"),
        @Index(name = "idx_call_participant_call_user", columnList = "call_session_id,user_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_call_participant_call_user", columnNames = {"call_session_id", "user_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class CallParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "call_session_id", nullable = false)
    private CallSession callSession;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "identity", nullable = false, length = 128)
    private String identity;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 16)
    private CallParticipantRole role;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 24)
    private CallParticipantStatus status;

    @Column(name = "joined_at")
    private LocalDateTime joinedAt;

    @Column(name = "accepted_at")
    private LocalDateTime acceptedAt;

    @Column(name = "left_at")
    private LocalDateTime leftAt;

    @Column(name = "declined_at")
    private LocalDateTime declinedAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
