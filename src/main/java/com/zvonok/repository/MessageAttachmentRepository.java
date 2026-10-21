package com.zvonok.repository;

import com.zvonok.model.MessageAttachment;
import com.zvonok.model.enumeration.AttachmentType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
    @Query("""
        select a from MessageAttachment a join fetch a.message m join fetch m.sender
        where m.room.id = :roomId and m.deletedAt is null
          and (cast(:cursorDate as timestamp) is null or
              (:ascending = false and (m.sentAt < :cursorDate or (m.sentAt = :cursorDate and a.id < :cursorId))) or
              (:ascending = true and (m.sentAt > :cursorDate or (m.sentAt = :cursorDate and a.id > :cursorId))))
          and a.type in :types
          and locate(:query, lower(a.originalFileName)) > 0
        """)
    List<MessageAttachment> library(long roomId, LocalDateTime cursorDate, long cursorId, boolean ascending,
            Set<AttachmentType> types, String query, Pageable pageable);
    List<MessageAttachment> findAllByStorageKey(String storageKey);
}
