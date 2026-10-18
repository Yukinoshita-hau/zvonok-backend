package com.zvonok.repository;

import com.zvonok.model.MessageAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MessageAttachmentRepository extends JpaRepository<MessageAttachment, Long> {
    List<MessageAttachment> findAllByStorageKey(String storageKey);
}
