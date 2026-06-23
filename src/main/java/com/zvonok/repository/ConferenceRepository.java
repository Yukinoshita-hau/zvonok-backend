package com.zvonok.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.zvonok.model.Conference;

public interface ConferenceRepository extends JpaRepository<Conference, Long> {
	
	Optional<Conference> findByCode(String code);

	boolean existsByCode(String code);
}
