package com.zvonok.repository;

import com.zvonok.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {
    @Query("select m.room.id from Message m where m.id = :messageId")
    Long findRoomIdByMessageId(@Param("messageId") Long messageId);

    Message findFirstByRoomIdAndDeletedAtIsNullOrderByIdDesc(Long roomId);

    Optional<Message> findBySenderUsernameAndUploadRequestKey(String username, String key);


	List<Message> findByRoomIdAndDeletedAtIsNullOrderByIdAsc(Long roomId);

	Message findFirstByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNullOrderByIdAsc(
			Long roomId, Long lastReadMessageId, Long senderId);

	Message findFirstByRoomIdAndSenderIdNotAndDeletedAtIsNullOrderByIdAsc(Long roomId, Long userId);

	int countByRoomIdAndIdGreaterThanAndDeletedAtIsNull(Long roomId, Long lastReadMessageId);

	int countByRoomIdAndDeletedAtIsNull(Long roomId);

	int countByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNull(Long roomId,
			long lastReadMessageId, Long senderId);

	int countByRoomIdAndSenderIdNotAndDeletedAtIsNull(Long roomId, Long senderId);


	@EntityGraph(attributePaths = {"sender", "room"})
	Page<Message> findByRoomIdAndDeletedAtIsNullOrderByIdDesc(Long roomId, Pageable pageable);

	@EntityGraph(attributePaths = {"sender", "room"})
	Page<Message> findByRoomIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long roomId,
			Long beforeId, Pageable pageable);

	@Modifying
	@Query("update Message m set m.deletedAt = :deletedAt where m.room.id = :roomId and m.deletedAt is null")
	int softDeleteAllByRoomId(@Param("roomId") Long roomId,
			@Param("deletedAt") LocalDateTime deletedAt);
}

