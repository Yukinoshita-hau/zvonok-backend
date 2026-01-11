package com.zvonok.repository;

import com.zvonok.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    // @Query("SELECT u FROM User u WHERE u.username = :value OR u.email = :value")
    // Optional<User> findByUsernameOrEmail(@Param("value") String value);
    Optional<User> findByUsername(String username);
    Optional<User> findByEmail(String email);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    List<User> findAllByUsernameIn(List<String> members);
}
