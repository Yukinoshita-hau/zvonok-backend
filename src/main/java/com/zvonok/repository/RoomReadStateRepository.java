package com.zvonok.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.zvonok.model.RoomReadState;

public interface RoomReadStateRepository extends JpaRepository<RoomReadState, Long> {
	Optional<RoomReadState> findByUserIdAndRoomId(Long userId, Long roomId);

	List<RoomReadState> findAllByUserIdAndRoomIdIn(Long userId, List<Long> roomId);
}
