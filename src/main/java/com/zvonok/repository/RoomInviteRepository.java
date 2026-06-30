package com.zvonok.repository;

import com.zvonok.model.RoomInvite;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomInviteRepository extends JpaRepository<RoomInvite, Long> {

	Optional<RoomInvite> findByTokenHashAndActiveTrue(String tokenHash);
}
