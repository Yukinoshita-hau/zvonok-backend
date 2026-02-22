package com.zvonok.repository;

import com.zvonok.model.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageRepository extends JpaRepository<Message, Long> {

	List<Message> findByRoomIdAndDeletedAtIsNullOrderBySentAtAsc(Long roomId);

	Page<Message> findByRoomIdAndDeletedAtIsNullOrderBySentAtDesc(Long roomId, Pageable pageable);

	Page<Message> findByRoomIdAndDeletedAtIsNullAndIdLessThanOrderByIdDesc(Long roomId,
			Long beforeId, Pageable pageable);
}

