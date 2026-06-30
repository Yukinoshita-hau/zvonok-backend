package com.zvonok.repository;

import com.zvonok.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface UserRepository extends JpaRepository<User, Long> {
	Optional<User> findByUsername(String username);

	Optional<User> findByEmail(String email);

	boolean existsByUsername(String username);

	boolean existsByEmail(String email);

	List<User> findAllByUsernameIn(List<String> members);

	List<User> findAllByIdIn(List<Long> ids);

	@Query("""
			    select u.username
			    from User u
			    where u.id in :ids
			""")
	Set<String> findUsernamesByIds(@Param("ids") Set<Long> ids);
}
