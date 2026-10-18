package com.zvonok.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.zvonok.model.RoomReadState;

public interface RoomReadStateRepository extends JpaRepository<RoomReadState, Long> {
	@Query("""
			select m.room.id as roomId, count(m.id) as unreadCount,
			       min(m.id) as firstUnreadMessageId
			from Message m
			left join RoomReadState s on s.room.id = m.room.id and s.user.id = :userId
			where m.room.id in :roomIds and m.sender.id <> :userId
			  and m.deletedAt is null
			  and (s.lastReadMessageId is null or m.id > s.lastReadMessageId)
			group by m.room.id
			""")
	List<RoomUnreadSummary> summarizeUnread(@Param("userId") Long userId,
			@Param("roomIds") List<Long> roomIds);
	Optional<RoomReadState> findByUserIdAndRoomId(Long userId, Long roomId);

	List<RoomReadState> findAllByUserIdAndRoomIdIn(Long userId, List<Long> roomId);

	@Modifying
	@Query("update RoomReadState r set r.lastReadMessageId = null, r.updatedAt = CURRENT_TIMESTAMP where r.room.id = :roomId")
	int resetByRoomId(@Param("roomId") Long roomId);
}
