package com.zvonok.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.zvonok.exception.MessageTargetValidationException;
import com.zvonok.exception_handler.enumeration.BusinessRuleMessage;
import com.zvonok.model.enumeration.MessageType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.SQLRestriction;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@SQLRestriction("room_id is not null")
@Table(name = "message", uniqueConstraints = @UniqueConstraint(name = "uk_message_sender_upload_request",
        columnNames = {"sender_id", "upload_request_key"}))
public class Message {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "sender_id", nullable = false) @JsonIgnore
    private User sender;

    @ManyToOne @JoinColumn(name = "room_id")
    private Room room; // Для приватных и групповых сообщений

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type = MessageType.DEFAULT;
    
    private Long replyToMessageId;

    @Column(name = "upload_request_key", length = 36)
    private String uploadRequestKey;

    @Column(name = "upload_request_hash", length = 64)
    private String uploadRequestHash;

    private LocalDateTime editedAt; // null если не редактировалось

    private LocalDateTime deletedAt; // null если не удалено

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    @BatchSize(size = 50)
    private List<MessageAttachment> attachments = new ArrayList<>();
    
    public boolean isEdited() {
        return editedAt != null;
    }
    
    public boolean isDeleted() {
        return deletedAt != null;
    }
    
    @PrePersist
    @PreUpdate
    private void validate() {
        if (room == null) {
            throw new MessageTargetValidationException(
                    BusinessRuleMessage.BUSINESS_MESSAGE_TARGET_VALIDATION_FAILED_MESSAGE.getMessage());
        }
    }
}
