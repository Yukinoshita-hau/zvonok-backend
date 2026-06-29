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

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "message")
public class Message {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne @JoinColumn(name = "sender_id", nullable = false) @JsonIgnore
    private User sender;

    @ManyToOne @JoinColumn(name = "room_id")
    private Room room; // Для приватных и групповых сообщений

    @ManyToOne @JoinColumn(name = "channel_id")
    private Channel channel; // Для сообщений в каналах серверов

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageType type = MessageType.DEFAULT;
    
    private Long replyToMessageId;

    private LocalDateTime editedAt; // null если не редактировалось

    private LocalDateTime deletedAt; // null если не удалено

    @Column(nullable = false)
    private LocalDateTime sentAt;

    @OneToMany(mappedBy = "message", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
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
        if ((room == null && channel == null) || (room != null && channel != null)) {
            throw new MessageTargetValidationException(
                    BusinessRuleMessage.BUSINESS_MESSAGE_TARGET_VALIDATION_FAILED_MESSAGE.getMessage());
        }
    }
}
