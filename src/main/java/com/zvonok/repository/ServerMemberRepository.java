package com.zvonok.repository;

import com.zvonok.model.ServerMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ServerMemberRepository extends JpaRepository<ServerMember, Long> {
    Optional<ServerMember> findByUserIdAndServerId(Long userId, Long serverId);
    Optional<ServerMember> findByUserIdAndServerIdAndIsActiveTrue(Long userId, Long serverId);
    Optional<ServerMember> findByUserIdAndServerIdAndIsActiveFalse(Long userId, Long serverId);
    @Query("""
        SELECT sm FROM ServerMember sm 
        JOIN Channel c ON c.folder.server.id = sm.server.id 
        WHERE sm.user.id = :userId 
        AND c.id = :channelId 
        AND sm.isActive = true
        """)
    Optional<ServerMember> findByUserIdAndChannelId(@Param("userId") Long userId, @Param("channelId") Long channelId);
    @Query("""
        SELECT sm FROM ServerMember sm 
        JOIN ChannelFolder cf ON cf.server.id = sm.server.id 
        WHERE sm.user.id = :userId 
        AND cf.id = :folderId 
        AND sm.isActive = true
        """)
    Optional<ServerMember> findByUserIdAndFolderId(@Param("userId") Long userId, @Param("folderId") Long folderId);
    @Query("""
        SELECT sm FROM ServerMember sm 
        JOIN sm.memberRoles mr 
        WHERE mr.role.id = :roleId 
        AND sm.isActive = true
        """)
    List<ServerMember> findByRoleId(@Param("roleId") Long roleId);
    @Query("""
        SELECT COUNT(mr) > 0 FROM ServerMemberRole mr 
        WHERE mr.member.id = :memberId 
        AND mr.role.id = :roleId
        """)
    boolean hasRole(@Param("memberId") Long memberId, @Param("roleId") Long roleId);
    long countByServerIdAndIsActiveTrue(Long serverId);

    List<ServerMember> findByServerIdAndIsActiveTrue(Long serverId);

    @Query("""
        SELECT COUNT(s) > 0 FROM Server s 
        WHERE s.owner.id = :userId 
        AND s.id = :serverId
        """)
    boolean isServerOwner(@Param("userId") Long userId, @Param("serverId") Long serverId);
}
