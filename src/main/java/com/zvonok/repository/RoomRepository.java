package com.zvonok.repository;

import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface RoomRepository extends JpaRepository<Room, Long> {
	@Query("SELECT r FROM Room r JOIN r.members m1 JOIN r.members m2 "
			+ "WHERE r.type = 'PRIVATE' AND r.isActive = true "
			+ "AND m1.id = :userId1 AND m2.id = :userId2 " + "AND SIZE(r.members) = 2")
	Optional<Room> findPrivateRoomBetweenUsers(@Param("userId1") Long userId1,
			@Param("userId2") Long userId2);

	@Query("""
			    select distinct u2.id
			    from Room r
			    join r.members u1
			    join r.members u2
			    where u1.id = :userId
			""")
	Set<Long> findUserIdsSharingRoomsWithUser(@Param("userId") Long userId);

	List<Room> findAllByTypeAndIsActiveTrue(RoomType type);

	List<Room> findAllByMembersContainingAndIsActiveTrue(User user);

	@Query("SELECT SIZE(r.members) FROM Room r WHERE r.id = :roomId")
	Integer countMembersInRoom(@Param("roomId") Long roomId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("""
		select r from Room r
		where r.id = :roomId
	""")
	Optional<Room> findByIdForUpdate(
		@Param("roomId") Long roomId
	);
}

