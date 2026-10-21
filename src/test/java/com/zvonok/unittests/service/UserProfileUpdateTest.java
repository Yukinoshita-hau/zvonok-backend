package com.zvonok.unittests.service;

import com.zvonok.model.User;
import com.zvonok.repository.FriendRequestRepository;
import com.zvonok.repository.FriendshipRepository;
import com.zvonok.repository.RoomRepository;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.S3Service;
import com.zvonok.service.UserEventPublisherService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.UpdateUserDto;
import jakarta.validation.Validation;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserProfileUpdateTest {
    private final UserRepository repository = mock(UserRepository.class);
    private final UserEventPublisherService publisher = mock(UserEventPublisherService.class);
    private final UserService service = new UserService(repository, publisher, mock(S3Service.class),
            mock(FriendshipRepository.class), mock(FriendRequestRepository.class), mock(RoomRepository.class));
    private final User stored = new User();

    @BeforeEach void setup() {
        stored.setId(7L); stored.setUsername("alice"); stored.setDisplayName("Alice");
        stored.setAboutMe("Existing biography"); stored.setAvatarUrl("https://test/avatar");
        when(repository.findByUsername("alice")).thenReturn(Optional.of(stored));
        when(repository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @ParameterizedTest @NullAndEmptySource @ValueSource(strings = {"   ", "Unicode: Привет 🌍"})
    void biographyMissingOrNullPreservesAndEmptyClearsOnReadback(String biography) {
        UpdateUserDto dto = new UpdateUserDto(); dto.setAboutMe(biography);
        var response = service.updateMyUser("alice", dto);
        String expected = biography == null ? "Existing biography" : biography;
        assertEquals(expected, response.getAboutMe());
        assertEquals(expected, service.getMyUser("alice").getAboutMe());
        assertEquals("Alice", response.getDisplayName());
        assertEquals("https://test/avatar", response.getAvatarUrl());
        verify(repository).save(stored); verify(publisher).publishUserProfileUpdated(stored);
    }

    @Test void updatesOnlyAuthenticatedUserAndKeepsResponseShape() {
        UpdateUserDto dto = new UpdateUserDto(); dto.setDisplayName("Новое имя");
        var response = service.updateMyUser("alice", dto);
        assertEquals(7L, response.getId()); assertEquals("alice", response.getUsername());
        assertEquals("Новое имя", service.getMyUser("alice").getDisplayName());
        assertThrows(RuntimeException.class, () -> service.updateMyUser("unknown", dto));
        verify(repository, times(1)).save(any());
    }

    @Test void validatesConfirmedDtoLimits() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator(); var dto = new UpdateUserDto();
            assertTrue(validator.validate(dto).isEmpty());
            for (String invalid : new String[]{"", "  ", "   ", "a".repeat(51)}) {
                dto.setDisplayName(invalid); assertFalse(validator.validate(dto).isEmpty());
            }
            dto.setDisplayName("Имя"); dto.setAboutMe("a".repeat(1000));
            assertTrue(validator.validate(dto).isEmpty());
            dto.setAboutMe("a".repeat(1001)); assertFalse(validator.validate(dto).isEmpty());
        }
    }
}
