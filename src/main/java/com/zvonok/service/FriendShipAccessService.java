package com.zvonok.service;

import org.springframework.stereotype.Service;
import com.zvonok.repository.FriendshipRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FriendShipAccessService {

	private final FriendshipRepository friendshipRepository;

	public boolean areFriends(Long firstUserId, Long secondUserId) {
		Long[] normalizedPair = normalizePair(firstUserId, secondUserId);
		return friendshipRepository.existsByUserOneIdAndUserTwoId(normalizedPair[0],
				normalizedPair[1]);
	}

	private Long[] normalizePair(Long first, Long second) {
		return first <= second ? new Long[] {first, second} : new Long[] {second, first};
	}
}
