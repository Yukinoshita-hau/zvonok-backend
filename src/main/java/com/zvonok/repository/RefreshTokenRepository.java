package com.zvonok.repository;

import com.zvonok.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByToken(String token);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken rt set rt.revoked = true
            where rt.id = :id and rt.revoked = false and rt.expiresAt > :now
            """)
    int consume(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying
    @Query("update RefreshToken rt set rt.revoked = true where rt.user.id = :userId and rt.revoked = false")
    int revokeAllActiveByUserId(@Param("userId") Long userId);

    @Modifying
    @Query("delete from RefreshToken rt where rt.expiresAt < :cutoff")
    int deleteAllExpired(@Param("cutoff") LocalDateTime cutoff);
}

