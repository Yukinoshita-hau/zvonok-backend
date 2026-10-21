package com.zvonok.unittests.security;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.S3Object;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.PlatformTransactionManager;
import com.zvonok.config.SecurityConfig;
import com.zvonok.controller.LiveKitController;
import com.zvonok.controller.S3Controller;
import com.zvonok.controller.MessageAttachmentController;
import com.zvonok.controller.UserController;
import com.zvonok.exception_handler.GlobalExceptionHandler;
import com.zvonok.model.*;
import com.zvonok.repository.*;
import com.zvonok.security.*;
import com.zvonok.service.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.mock.web.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StorageHttpSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    WebSocketAclSecurityTest fixture;
    AmazonS3 s3;
    MessageAttachmentRepository attachments;
    UserRepository users;
    String token;
    Message message;
    MessageAttachment attachment;

    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "security.allowedOrigins", "http://localhost", "s3.localEndpoint", "http://localhost/api/s3/download",
                "app.jwt.ExpirationMs", "60000", "app.jwt.secret", Base64.getEncoder().encodeToString(new byte[64]))));
        context.register(Config.class); context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        fixture = context.getBean(WebSocketAclSecurityTest.class); s3 = context.getBean(AmazonS3.class);
        attachments = context.getBean(MessageAttachmentRepository.class); users = context.getBean(UserRepository.class);
        User alice = fixture.users.getUser("alice");
        when(users.findByUsername("alice")).thenReturn(Optional.of(alice));
        token = "Bearer " + fixture.principal.getToken();
        message = new Message(); message.setId(100L); message.setRoom(fixture.room);
        attachment = new MessageAttachment(); attachment.setId(7L); attachment.setMessage(message);
        attachment.setStorageKey("message-attachments/100/old.png"); attachment.setContentType("image/png");
        attachment.setOriginalFileName("old.png"); attachment.setSizeBytes(3L);
        when(fixture.messages.findById(100L)).thenReturn(Optional.of(message));
        when(attachments.findById(7L)).thenReturn(Optional.of(attachment));
        when(attachments.findAllByStorageKey(attachment.getStorageKey())).thenReturn(List.of(attachment));
        when(s3.getObject(anyString(), anyString())).thenAnswer(invocation -> {
            S3Object object = new S3Object(); object.setObjectContent(new ByteArrayInputStream(new byte[]{1,2,3}));
            object.getObjectMetadata().setContentLength(3); object.getObjectMetadata().setContentType("image/png"); return object;
        });
    }
    @AfterEach void close() { context.close(); }

    @Test void publicOldAvatarStillWorks() throws Exception {
        when(users.existsByAvatarUrl("http://localhost/api/s3/download/alice_123.png")).thenReturn(true);
        mvc.perform(get("/s3/download/alice_123.png")).andExpect(status().isOk()).andExpect(content().contentType("image/png"));
        verify(s3).getObject("zvonok-data", "alice_123.png");
    }
    @Test void existingGroupRoomAvatarRemainsPublic() throws Exception {
        when(fixture.rooms.existsByAvatarUrlAndIsActiveTrue("http://localhost/api/s3/download/group.png")).thenReturn(true);
        mvc.perform(get("/s3/download/group.png")).andExpect(status().isOk());
    }
    @Test void avatarUploadUsesAuthenticatedOwnerAndUniqueKey() throws Exception {
        byte[] png = Base64.getDecoder().decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aZ1cAAAAASUVORK5CYII=");
        mvc.perform(multipart("/users/upload-avatar").file(new MockMultipartFile("file", "bob.png", "image/png", png))
                .header("Authorization", token)).andExpect(status().isOk());
        org.junit.jupiter.api.Assertions.assertTrue(fixture.users.getUser("alice").getAvatarUrl()
                .startsWith("http://localhost/api/s3/download/avatar_"));
        verify(users).save(fixture.users.getUser("alice"));
        verify(s3).putObject(any(com.amazonaws.services.s3.model.PutObjectRequest.class));
        verify(s3, never()).deleteObject(anyString(), anyString());
    }
    @Test void unknownLegacyObjectClosedWithoutStorageRead() throws Exception {
        mvc.perform(get("/s3/download/unknown.png")).andExpect(status().isNotFound()); verifyNoInteractions(s3);
    }
    @Test void oldAvatarWithMissingMimeRemainsDownloadable() throws Exception {
        when(users.existsByAvatarUrl("http://localhost/api/s3/download/old.png")).thenReturn(true);
        when(s3.getObject(anyString(), anyString())).thenAnswer(invocation -> {
            S3Object object = new S3Object(); object.setObjectContent(new ByteArrayInputStream(new byte[]{1,2,3}));
            object.getObjectMetadata().setContentLength(3); return object;
        });
        mvc.perform(get("/s3/download/old.png")).andExpect(status().isOk())
                .andExpect(content().contentType("application/octet-stream"));
    }
    @Test void genericUploadCannotOverwriteOldObject() throws Exception {
        mvc.perform(multipart("/s3/upload").file(new MockMultipartFile("file", "alice_123.png", "image/png", new byte[]{1}))
                .header("Authorization", token)).andExpect(status().isForbidden()); verifyNoInteractions(s3);
    }
    @Test void cannotDeleteForeignAvatar() throws Exception {
        mvc.perform(delete("/s3/delete/bob.png").header("Authorization", token)).andExpect(status().isForbidden()); verifyNoInteractions(s3);
    }
    @Test void cannotDeleteForeignAttachment() throws Exception {
        mvc.perform(delete("/s3/delete/attachment.png").header("Authorization", token)).andExpect(status().isForbidden()); verifyNoInteractions(s3);
    }
    @Test void anonymousCannotMutateStorage() throws Exception {
        mvc.perform(delete("/s3/delete/bob.png")).andExpect(status().isUnauthorized()); verifyNoInteractions(s3);
    }
    @Test void authorizedMemberCanReadOldAttachmentById() throws Exception {
        mvc.perform(get("/message-attachments/7/download").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }
    @Test void genericNestedKeyCannotBypassAttachmentAcl() throws Exception {
        mvc.perform(get("/s3/download/message-attachments/100/old.png")).andExpect(status().isNotFound()); verifyNoInteractions(s3);
    }
    @Test void authorizedGenericNestedKeyUsesDomainAcl() throws Exception {
        mvc.perform(get("/s3/download/message-attachments/100/old.png").header("Authorization", token)).andExpect(status().isOk());
    }
    @Test void removedMemberCannotReadEitherDownloadRoute() throws Exception {
        fixture.room.setMembers(List.of());
        mvc.perform(get("/message-attachments/7/download").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/s3/download/message-attachments/100/old.png").header("Authorization", token)).andExpect(status().isNotFound());
        verifyNoInteractions(s3);
    }
    @Test void deletedMessageCannotReadAttachment() throws Exception {
        message.setDeletedAt(LocalDateTime.now());
        mvc.perform(get("/message-attachments/7/download").header("Authorization", token)).andExpect(status().isNotFound()); verifyNoInteractions(s3);
    }
    @Test void flatLegacyAttachmentDoesNotBecomePublicViaAvatarAlias() throws Exception {
        when(attachments.findAllByStorageKey("legacy.png")).thenReturn(List.of(attachment));
        when(users.existsByAvatarUrl("http://localhost/api/s3/download/legacy.png")).thenReturn(true);
        mvc.perform(get("/s3/download/legacy.png")).andExpect(status().isNotFound()); verifyNoInteractions(s3);
    }
    @Test void removedCanvasObjectsAreNotServedFromGenericStorage() throws Exception {
        mvc.perform(get("/s3/download/canvas_board_1.png")).andExpect(status().isNotFound());
        mvc.perform(get("/s3/download/canvas_board_1.png").header("Authorization", token)).andExpect(status().isNotFound());
        verifyNoInteractions(s3);
    }
    @Test void removedFeatureRestEndpointsHaveNoHandler() throws Exception {
        mvc.perform(get("/calls/40/boards").header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/code-sessions/active").param("callSessionId", "40")
                .header("Authorization", token)).andExpect(status().isNotFound());
        mvc.perform(get("/code-runs/languages").header("Authorization", token)).andExpect(status().isNotFound());
        verifyNoInteractions(s3);
    }
    @Test void authenticatedLegacyLivekitCannotMintArbitraryRoom() throws Exception {
        mvc.perform(get("/livekit/token").param("room", "victim").header("Authorization", token)).andExpect(status().isForbidden());
    }
    @Test void anonymousLegacyLivekitRequiresJwt() throws Exception {
        mvc.perform(get("/livekit/token").param("room", "victim")).andExpect(status().isUnauthorized());
    }

    @Test void profileUpdateRequiresAuthentication() throws Exception {
        mvc.perform(put("/users/my").contentType("application/json").content("{\"aboutMe\":\"\"}"))
                .andExpect(status().isUnauthorized());
        verify(users, never()).save(any());
    }

    @Test void profileValidationRejectsInvalidFieldsBeforePersistence() throws Exception {
        for (String body : List.of("{\"displayName\":\"\"}", "{\"displayName\":\"   \"}",
                "{\"displayName\":\"" + "a".repeat(51) + "\"}", "{\"aboutMe\":\"" + "a".repeat(1001) + "\"}")) {
            mvc.perform(put("/users/my").header("Authorization", token).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(users, never()).save(any());
    }

    @Test void profileClearingUsesPrincipalAndMissingOrNullPreservesBiography() throws Exception {
        when(users.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        var alice = fixture.users.getUser("alice"); alice.setAboutMe("Existing biography");
        for (String body : List.of("{}", "{\"aboutMe\":null}")) {
            mvc.perform(put("/users/my").header("Authorization", token).contentType("application/json").content(body))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.aboutMe").value("Existing biography"));
        }
        mvc.perform(put("/users/my").header("Authorization", token).contentType("application/json")
                .content("{\"username\":\"bob\",\"id\":999,\"aboutMe\":\"\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.aboutMe").value(""));
        verify(users, never()).findByUsername("bob");
        mvc.perform(get("/users/my").header("Authorization", token)).andExpect(status().isOk())
                .andExpect(jsonPath("$.aboutMe").value(""));
    }

    @Configuration(proxyBeanMethods=false) @EnableWebMvc
    @Import({SecurityConfig.class, S3Controller.class, MessageAttachmentController.class, LiveKitController.class,
            UserController.class, GlobalExceptionHandler.class, S3ObjectAccessService.class, S3Service.class})
    static class Config {
        @Bean WebSocketAclSecurityTest fixture() { var f = new WebSocketAclSecurityTest(); f.setup(); return f; }
        @Bean JwtTokenProvider tokens(WebSocketAclSecurityTest f) { return f.tokens; }
        @Bean JwtAuthenticationFilter jwt(JwtTokenProvider t) { return new JwtAuthenticationFilter(t); }
        @Bean ObjectMapper mapper() { return Jackson2ObjectMapperBuilder.json().build(); }
        @Bean CustomAuthenticationEntryPoint entry(ObjectMapper m) { return new CustomAuthenticationEntryPoint(m); }
        @Bean AmazonS3 s3() { return mock(AmazonS3.class); }
        @Bean MessageAttachmentRepository attachments() { return mock(MessageAttachmentRepository.class); }
        @Bean UserRepository users() { return mock(UserRepository.class); }
        @Bean UserService actualUsers(UserRepository repository, S3Service storage) {
            return new UserService(repository, mock(UserEventPublisherService.class), storage,
                    mock(FriendshipRepository.class), mock(FriendRequestRepository.class), mock(RoomRepository.class));
        }
        @Bean UserSettingsService settings() { return mock(UserSettingsService.class); }
        @Bean MessageAccessService access(WebSocketAclSecurityTest f) { return f.messageAccess; }
        @Bean RoomRepository roomReferences(WebSocketAclSecurityTest f) { return f.rooms; }
        @Bean MessageService messages(WebSocketAclSecurityTest f, MessageAttachmentRepository attachments, S3Service storage) {
            return new MessageService(f.messages, f.messageAccess, mock(SimpMessagingTemplate.class), mock(RoomService.class),
                    f.users, attachments, storage, mock(RoomReadStateService.class), mock(PlatformTransactionManager.class));
        }
    }
}
