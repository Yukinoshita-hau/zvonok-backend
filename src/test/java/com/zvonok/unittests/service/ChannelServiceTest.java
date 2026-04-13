package com.zvonok.unittests.service;

import com.zvonok.exception.ChannelNotFoundException;
import com.zvonok.exception.InsufficientPermissionsException;
import com.zvonok.exception_handler.enumeration.HttpResponseMessage;
import com.zvonok.model.Channel;
import com.zvonok.model.ChannelFolder;
import com.zvonok.model.User;
import com.zvonok.repository.ChannelRepository;
import com.zvonok.repository.MessageRepository;
import com.zvonok.service.ChannelFolderService;
import com.zvonok.service.ChannelService;
import com.zvonok.service.PermissionService;
import com.zvonok.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelServiceTest {

	@Mock
	private ChannelRepository channelRepository;

	@Mock
	private MessageRepository messageRepository;

	@Mock
	private ChannelFolderService channelFolderService;

	@Mock
	private UserService userService;

	@Mock
	private PermissionService permissionService;

	@InjectMocks
	private ChannelService channelService;

	@Test
	void getChannelShouldReturnChannelWhenUserCanViewIt() {
		User user = createUser(11L, "alice");
		Channel channel = createChannel(7L, 2L);
		when(userService.getUser("alice")).thenReturn(user);
		when(channelRepository.findByIdAndFolderId(7L, 2L)).thenReturn(Optional.of(channel));
		when(permissionService.canUserViewChannel(11L, 7L)).thenReturn(true);

		Channel result = channelService.getChannel("alice", 2L, 7L);

		assertSame(channel, result);
		verify(permissionService).canUserViewChannel(11L, 7L);
	}

	@Test
	void getChannelShouldThrowForbiddenWhenUserCannotViewChannel() {
		User user = createUser(11L, "alice");
		Channel channel = createChannel(7L, 2L);
		when(userService.getUser("alice")).thenReturn(user);
		when(channelRepository.findByIdAndFolderId(7L, 2L)).thenReturn(Optional.of(channel));
		when(permissionService.canUserViewChannel(11L, 7L)).thenReturn(false);

		InsufficientPermissionsException exception = assertThrows(
				InsufficientPermissionsException.class,
				() -> channelService.getChannel("alice", 2L, 7L));

		org.junit.jupiter.api.Assertions.assertEquals(
				HttpResponseMessage.HTTP_INSUFFICIENT_PERMISSIONS_RESPONSE_MESSAGE.getMessage(),
				exception.getMessage());
	}

	@Test
	void getChannelShouldThrowNotFoundWhenChannelDoesNotBelongToFolder() {
		when(userService.getUser("alice")).thenReturn(createUser(11L, "alice"));
		when(channelRepository.findByIdAndFolderId(7L, 2L)).thenReturn(Optional.empty());

		assertThrows(ChannelNotFoundException.class,
				() -> channelService.getChannel("alice", 2L, 7L));

		verify(permissionService, never()).canUserViewChannel(11L, 7L);
	}

	private User createUser(Long id, String username) {
		User user = new User();
		user.setId(id);
		user.setUsername(username);
		return user;
	}

	private Channel createChannel(Long channelId, Long folderId) {
		ChannelFolder folder = new ChannelFolder();
		folder.setId(folderId);

		Channel channel = new Channel();
		channel.setId(channelId);
		channel.setFolder(folder);
		channel.setName("general");
		return channel;
	}
}
