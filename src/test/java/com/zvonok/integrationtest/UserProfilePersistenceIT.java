package com.zvonok.integrationtest;

import com.zvonok.model.User;
import com.zvonok.repository.FriendRequestRepository;
import com.zvonok.repository.FriendshipRepository;
import com.zvonok.repository.RoomRepository;
import com.zvonok.repository.UserRepository;
import com.zvonok.service.S3Service;
import com.zvonok.service.UserEventPublisherService;
import com.zvonok.service.UserService;
import com.zvonok.service.dto.UpdateUserDto;
import jakarta.persistence.Entity;
import java.util.Optional;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Testcontainers
class UserProfilePersistenceIT {
    @Container static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Test void serviceClearsBiographyAndFreshSessionReadsEmptyValue() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.url", postgres.getJdbcUrl())
                .applySetting("hibernate.connection.username", postgres.getUsername())
                .applySetting("hibernate.connection.password", postgres.getPassword())
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy")
                .applySetting("hibernate.globally_quoted_identifiers", true).build();
        try {
            var metadata = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var definition : scanner.findCandidateComponents("com.zvonok.model")) metadata.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
            try (var factory = metadata.buildMetadata().buildSessionFactory()) {
                Long id;
                try (var session = factory.openSession()) {
                    var tx = session.beginTransaction(); var user = new User();
                    user.setUsername("profile-fixture"); user.setDisplayName("Profile fixture");
                    user.setEmail("profile@example.invalid"); user.setPassword("fixture-hash"); user.setAboutMe("Before");
                    session.persist(user); tx.commit(); id = user.getId();
                }
                var repository = mock(UserRepository.class);
                when(repository.findByUsername("profile-fixture")).thenAnswer(invocation -> {
                    try (var session = factory.openSession()) {return Optional.of(session.find(User.class, id));}
                });
                when(repository.save(any(User.class))).thenAnswer(invocation -> {
                    try (var session = factory.openSession()) {
                        var tx = session.beginTransaction(); var saved = session.merge((User) invocation.getArgument(0)); tx.commit(); return saved;
                    }
                });
                var service = new UserService(repository, mock(UserEventPublisherService.class), mock(S3Service.class),
                        mock(FriendshipRepository.class), mock(FriendRequestRepository.class), mock(RoomRepository.class));
                var dto = new UpdateUserDto(); dto.setAboutMe("");
                assertEquals("", service.updateMyUser("profile-fixture", dto).getAboutMe());
                assertEquals("", service.getMyUser("profile-fixture").getAboutMe());
                dto.setAboutMe(null); service.updateMyUser("profile-fixture", dto);
                assertEquals("", service.getMyUser("profile-fixture").getAboutMe());
            }
        } finally {StandardServiceRegistryBuilder.destroy(registry);}
    }
}
