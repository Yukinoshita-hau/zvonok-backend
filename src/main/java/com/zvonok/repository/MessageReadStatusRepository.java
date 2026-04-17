package com.zvonok.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import com.zvonok.model.MessageReadStatus;

public interface MessageReadStatusRepository extends JpaRepository<MessageReadStatus, Long> {
	Optional<MessageReadStatus> findByMessageIdAndUserId(Long messageId, Long userId);
	Optional<MessageReadStatus> findByMessageId(Long messageId);

	@Modifying
	@Query(value = """
		INSERT INTO message_read_status (message_id, user_id, read_at)
		VALUES (:messageId, :userId, :readAt)
		ON CONFLICT (message_id, user_id) DO NOTHING
	""", nativeQuery = true)
	int insertIngnore(@Param("messageId") Long messageId, @Param("userId") Long userId, @Param("readAt") LocalDateTime readAt);
}
