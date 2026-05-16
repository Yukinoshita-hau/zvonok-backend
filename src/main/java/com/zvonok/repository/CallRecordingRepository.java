package com.zvonok.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.zvonok.model.CallRecording;
import com.zvonok.model.enumeration.CallRecordingStatus;

public interface CallRecordingRepository extends JpaRepository<CallRecording, Long> {
	Optional<CallRecording> findFirstByCallSessionIdAndStatusInOrderByStartedAtDesc(Long callSessionId, Collection<CallRecordingStatus> statuses);

	List<CallRecording> findAllByCallSessionIdOrderByStartedAtDesc(Long callSessionId);

	Optional<CallRecording> findByEgressId(String egressId);
}
