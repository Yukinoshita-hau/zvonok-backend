package com.zvonok.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zvonok.model.RoomReadState;

public interface RoomReadStateRepository extends JpaRepository<RoomReadState, Long> {
	Optional<RoomReadState> findByUserIdAndRoomId(Long userId, Long roomId);

	List<RoomReadState> findAllByUserIdAndRoomIdIn(Long userId, List<Long> roomId);

	@Modifying
	@Query("update RoomReadState r set r.lastReadMessageId = null, r.updatedAt = CURRENT_TIMESTAMP where r.room.id = :roomId")
	int resetByRoomId(@Param("roomId") Long roomId);
}
