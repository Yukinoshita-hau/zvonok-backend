package com.zvonok.integrationtest;

import com.zvonok.exception.MessageNotFoundException;
import com.zvonok.model.Message;
import com.zvonok.model.Room;
import com.zvonok.model.User;
import com.zvonok.model.enumeration.RoomType;
import com.zvonok.repository.MessageRepository;
import com.zvonok.repository.MessageAttachmentRepository;
import com.zvonok.repository.RoomRepository;
import com.zvonok.service.MessageAccessService;
import com.zvonok.service.S3ObjectAccessService;
import com.zvonok.service.RoomAccessService;
import com.zvonok.service.UserService;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Both schema boots and all SQL run exclusively in this disposable PostgreSQL. */
@Testcontainers
class LegacyDataPreservationIT {
    @Container static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    private SessionFactory boot(String action) throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", postgres.getJdbcUrl())
                .applySetting("hibernate.connection.username", postgres.getUsername())
                .applySetting("hibernate.connection.password", postgres.getPassword())
                .applySetting("hibernate.hbm2ddl.auto", action)
                .applySetting("hibernate.physical_naming_strategy",
                        "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.globally_quoted_identifiers", true).build();
        try {
            var metadata = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var definition : scanner.findCandidateComponents("com.zvonok.model")) {
                metadata.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
            }
            return metadata.buildMetadata().buildSessionFactory();
        } catch (Exception failure) {
            StandardServiceRegistryBuilder.destroy(registry);
            throw failure;
        }
    }

    @Test void updateStartupPreservesArchivedTablesRowsKeysAndRoomMessaging() throws Exception {
        Long userId;
        Long roomMessageId;
        // Fresh schema in disposable container only. No application datasource is loaded.
        try (var factory = boot("create"); var session = factory.openSession()) {
            var tx = session.beginTransaction();
            User user = new User(); user.setUsername("archive-test"); user.setDisplayName("Archive test");
            user.setEmail("archive@test.invalid"); user.setPassword("test-hash"); session.persist(user);
            Room room = new Room(); room.setType(RoomType.GROUP);
            room.setMembers(new ArrayList<>(List.of(user))); session.persist(room);
            Message message = new Message(); message.setRoom(room); message.setSender(user);
            message.setContent("retained room message"); message.setSentAt(LocalDateTime.now()); session.persist(message);
            tx.commit(); userId = user.getId(); roomMessageId = message.getId();
        }
        List<String> tables = List.of("server", "server_member", "server_role", "server_member_role", "server_ban",
                "channel_folder", "channel", "channel_permission_override", "folder_permission_override");
        Long archivedId;
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var sql = connection.createStatement()) {
            // Representative old tables, FK, unique/index and shared rows survive an update boot.
            for (String table : tables) {
                sql.execute("create table " + table + " (id bigint primary key, marker text not null unique)");
                sql.execute("insert into " + table + " values (1, 'preserve')");
            }
            sql.execute("alter table message add column channel_id bigint references channel(id)");
            sql.execute("create index legacy_message_channel_idx on message(channel_id)");
            try (var rows = sql.executeQuery("insert into message (sender_id, channel_id, content, type, sent_at) values ("
                    + userId + ", 1, 'archived', 'DEFAULT', now()) returning id")) {
                rows.next(); archivedId = rows.getLong(1);
            }
            sql.execute("insert into message_attachment (message_id, type, storage_key, original_file_name, content_type, size_bytes, created_at) values ("
                    + archivedId + ", 'IMAGE', 'message-attachments/archived', 'old.png', 'image/png', 12, now())");
            sql.execute("insert into message_read_status (message_id, user_id, read_at) values (" + archivedId + ", " + userId + ", now())");
        }
        try (var factory = boot("update"); var session = factory.openSession()) {
            assertNull(session.find(Message.class, archivedId), "Archived rows must not enter the active JPA model");
            assertEquals("retained room message", session.find(Message.class, roomMessageId).getContent());
            var repositories = new JpaRepositoryFactory(session);
            var messages = repositories.getRepository(MessageRepository.class);
            var users = mock(UserService.class);
            var access = new MessageAccessService(messages,
                    new RoomAccessService(repositories.getRepository(RoomRepository.class), users));
            assertThrows(MessageNotFoundException.class, () -> access.getReadableMessage(archivedId, "archive-test"));
            var storageAccess = new S3ObjectAccessService(
                    repositories.getRepository(MessageAttachmentRepository.class), access, null, null);
            assertThrows(MessageNotFoundException.class,
                    () -> storageAccess.requireRead("message-attachments/archived", "archive-test"));
            assertNull(messages.findRoomIdByMessageId(archivedId));
            assertTrue(messages.findAllById(List.of(archivedId, roomMessageId)).stream()
                    .allMatch(message -> message.getId().equals(roomMessageId)));
        }
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                var sql = connection.createStatement()) {
            for (String table : tables) {
                try (var rows = sql.executeQuery("select marker from " + table + " where id=1")) {
                    assertTrue(rows.next(), table); assertEquals("preserve", rows.getString(1));
                }
            }
            for (String query : List.of("select channel_id from message where id=" + archivedId,
                    "select size_bytes from message_attachment where message_id=" + archivedId,
                    "select user_id from message_read_status where message_id=" + archivedId,
                    "select 1 from pg_indexes where indexname='legacy_message_channel_idx'",
                    "select 1 from pg_constraint where conrelid='message'::regclass and confrelid='channel'::regclass")) {
                try (var rows = sql.executeQuery(query)) { assertTrue(rows.next(), query); }
            }
        }
    }
}
