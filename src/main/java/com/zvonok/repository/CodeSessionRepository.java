package com.zvonok.repository;

import com.zvonok.model.CodeSession;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CodeSessionRepository extends JpaRepository<CodeSession, Long> {

	Optional<CodeSession> findFirstByCallSessionIdAndActiveTrueOrderByCreatedAtDesc(
			Long callSessionId);

	Optional<CodeSession> findByIdAndCallSessionId(Long id, Long callSessionId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select s from CodeSession s where s.id = :id")
	Optional<CodeSession> findByIdForUpdate(@Param("id") Long id);
}
