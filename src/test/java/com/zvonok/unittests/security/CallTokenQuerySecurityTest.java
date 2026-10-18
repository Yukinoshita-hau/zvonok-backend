package com.zvonok.unittests.security;

import com.zvonok.repository.CallParticipantRepository;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;
import jakarta.persistence.Entity;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/** Parse the production update against actual entity mappings, without connecting to a database. */
class CallTokenQuerySecurityTest {
    @Test void membershipGuardIsValidHibernateQuery() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
                .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
                .applySetting("hibernate.hbm2ddl.auto", "none").build();
        try {
            var sources = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false);
            scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var definition : scanner.findCandidateComponents("com.zvonok.model")) {
                sources.addAnnotatedClass(Class.forName(definition.getBeanClassName()));
            }
            try (var factory = sources.buildMetadata().buildSessionFactory()) {
                var method = java.util.Arrays.stream(CallParticipantRepository.class.getMethods())
                        .filter(m -> m.getName().equals("touchLastSeenIfTokenAllowed")).findFirst().orElseThrow();
                String hql = method.getAnnotation(Query.class).value();
                assertDoesNotThrow(() -> factory.unwrap(SessionFactoryImplementor.class).getQueryEngine()
                        .getHqlTranslator().translate(hql, null));
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
