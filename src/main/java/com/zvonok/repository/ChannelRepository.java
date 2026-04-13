package com.zvonok.repository;

import com.zvonok.model.Channel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChannelRepository extends JpaRepository<Channel, Long> {
    Optional<Channel> findById(long id);
    List<Channel> findByFolderIdAndIsActiveTrue(Long folderId);
    List<Channel> findByFolderIdOrderByPosition(Long folderId);
    long countByFolderIdAndIsActiveTrue(Long folderId);
    Optional<Channel> findByIdAndFolderId(Long channelId, Long folderId);
}
