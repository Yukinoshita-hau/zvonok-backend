package com.zvonok.repository;

import com.zvonok.model.Friendship;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

	boolean existsByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

	Optional<Friendship> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

	List<Friendship> findByUserOneIdOrUserTwoId(Long userOneId, Long userTwoId);

	void deleteByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

	@Query("""
			    select distinct
			        case
			            when f.userOne.id = :userId then f.userTwo.id
			            else f.userOne.id
			        end
			    from Friendship f
			    where f.userOne.id = :userId
			       or f.userTwo.id = :userId
			""")
	Set<Long> findFriendIdsByUserId(@Param("userId") Long userId);
}

