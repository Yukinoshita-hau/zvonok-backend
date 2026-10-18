package com.zvonok.unittests.service;

import com.zvonok.exception.RefreshTokenRevokedException;
import com.zvonok.model.RefreshToken;
import com.zvonok.model.User;
import com.zvonok.repository.RefreshTokenRepository;
import com.zvonok.service.RefreshTokenService;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/** Real independent SQL transactions, production entity mappings, repository and rotation service. */
class RefreshRotationConcurrencyTest {
    private SessionFactory factory;
    private StandardServiceRegistry registry;
    private String token;

    @BeforeEach void setup() throws Exception {
        registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:refresh_" + UUID.randomUUID()
                        + ";NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000")
                .applySetting("hibernate.connection.pool_size", 12)
                .applySetting("hibernate.hbm2ddl.auto", "create-drop").build();
        var sources = new MetadataSources(registry);
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
        for (var definition : scanner.findCandidateComponents("com.zvonok.model")) {
            sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
        }
        factory = sources.buildMetadata().buildSessionFactory();
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var user = new User();
            user.setUsername("rotation"); user.setDisplayName("Rotation");
            user.setEmail("rotation@example.test"); user.setPassword("unused");
            em.persist(user);
            token = service(em).createToken(user).getToken();
            em.getTransaction().commit();
        }
    }

    private RefreshTokenService service(jakarta.persistence.EntityManager em) {
        var repository = new JpaRepositoryFactory(em).getRepository(RefreshTokenRepository.class);
        var service = new RefreshTokenService(repository);
        ReflectionTestUtils.setField(service, "refreshExpirationMs", 86400000L);
        return service;
    }

    @AfterEach void cleanup() {
        if (factory != null) factory.close();
        if (registry != null) StandardServiceRegistryBuilder.destroy(registry);
    }

    @ParameterizedTest @ValueSource(ints = {2, 10})
    void competingTransactionsProduceExactlyOneChild(int count) throws Exception {
        var barrier = new CyclicBarrier(count);
        try (var executor = Executors.newFixedThreadPool(count)) {
            var results = new ArrayList<Future<Boolean>>();
            for (int i = 0; i < count; i++) results.add(executor.submit(() -> {
                try (var em = factory.createEntityManager()) {
                    em.getTransaction().begin();
                    var service = service(em);
                    // All contenders really validate the same unconsumed row before any UPDATE.
                    RefreshToken old = service.validate(token);
                    barrier.await(15, TimeUnit.SECONDS);
                    try {
                        service.rotate(old);
                        em.getTransaction().commit();
                        return true;
                    } catch (RefreshTokenRevokedException expected) {
                        em.getTransaction().rollback();
                        return false;
                    }
                }
            }));
            int successes = 0;
            for (var result : results) if (result.get(30, TimeUnit.SECONDS)) successes++;
            assertEquals(1, successes);
        }
        try (var em = factory.createEntityManager()) {
            assertEquals(2L, em.createQuery("select count(rt) from RefreshToken rt", Long.class).getSingleResult());
            assertEquals(1L, em.createQuery("select count(rt) from RefreshToken rt where rt.revoked = false", Long.class).getSingleResult());
            assertThrows(RefreshTokenRevokedException.class, () -> service(em).validate(token));
        }
    }

    @Test void replayCannotRotateAnAlreadyConsumedToken() {
        try (var em = factory.createEntityManager()) {
            var service = service(em);
            em.getTransaction().begin();
            RefreshToken old = service.validate(token);
            service.rotate(old);
            assertThrows(RefreshTokenRevokedException.class, () -> service.rotate(old));
            em.getTransaction().commit();
        }
    }

    @Test void failedTransactionRollsBackConsumptionAndChild() {
        try (var em = factory.createEntityManager()) {
            var service = service(em);
            em.getTransaction().begin();
            service.rotate(service.validate(token));
            em.getTransaction().rollback();
        }
        try (var em = factory.createEntityManager()) {
            em.getTransaction().begin();
            var service = service(em);
            assertFalse(service.validate(token).isRevoked());
            service.rotate(service.validate(token));
            em.getTransaction().commit();
            assertEquals(2L, em.createQuery("select count(rt) from RefreshToken rt", Long.class).getSingleResult());
        }
    }
}
