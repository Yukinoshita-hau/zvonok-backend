package com.zvonok.repository;

import com.zvonok.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findByRoomIdAndDeletedAtIsNullOrderBySentAtAsc(Long roomId);

	Message findFirstByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNullOrderByIdAsc(
			Long roomId, Long lastReadMessageId, Long senderId);

	Message findFirstByRoomIdAndSenderIdNotAndDeletedAtIsNullOrderByIdAsc(Long roomId, Long userId);

	int countByRoomIdAndIdGreaterThanAndDeletedAtIsNull(Long roomId, Long lastReadMessageId);

	int countByRoomIdAndDeletedAtIsNull(Long roomId);

	int countByRoomIdAndIdGreaterThanAndSenderIdNotAndDeletedAtIsNull(Long roomId,
			long lastReadMessageId, Long senderId);

	int countByRoomIdAndSenderIdNotAndDeletedAtIsNull(Long roomId, Long senderId);


	Page<Message> findByRoomIdAndDeletedAtIsNullOrderBySentAtDesc(Long roomId, Pageable pageable);

	Page<Message> findByRoomIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long roomId,
			Long beforeId, Pageable pageable);

	Page<Message> findByChannelIdAndDeletedAtIsNullOrderBySentAtDesc(Long channelId,
			Pageable pageable);

	Page<Message> findByChannelIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long channelId,
			Long beforeId, Pageable pageable);

	@Modifying
	@Query("update Message m set m.deletedAt = :deletedAt where m.room.id = :roomId and m.deletedAt is null")
	int softDeleteAllByRoomId(@Param("roomId") Long roomId,
			@Param("deletedAt") LocalDateTime deletedAt);
}

