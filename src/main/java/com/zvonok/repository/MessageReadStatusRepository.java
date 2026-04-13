package com.zvonok.repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.zvonok.model.MessageReadStatus;

public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {
	Optional<MessageReadStatus> findByMessageIdAndUserId(Long messageId, Long userId);	
	List<MessageReadStatus> findByMessageId(Long messageId);
}
