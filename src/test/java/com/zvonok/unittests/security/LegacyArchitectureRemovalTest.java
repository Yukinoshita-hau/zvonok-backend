package com.zvonok.unittests.security;

import com.zvonok.model.Message;
import com.zvonok.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LegacyArchitectureRemovalTest {
    @Test void productionControllerMappingsContainCoreAndNoLegacyHandlers() throws Exception {
        var scanner = new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
        Set<String> paths = new HashSet<>();
        for (var definition : scanner.findCandidateComponents("com.zvonok.controller")) {
            Class<?> controller = Class.forName(definition.getBeanClassName());
            RequestMapping root = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            String[] prefixes = root == null ? new String[]{""} : root.path();
            for (var method : controller.getDeclaredMethods()) {
                RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (mapping == null) continue;
                for (String prefix : prefixes) {
                    for (String path : mapping.path()) paths.add(prefix + path);
                }
            }
        }
        assertTrue(paths.stream().anyMatch(p -> p.startsWith("/auth/")), paths.toString());
        for (String domain : new String[]{"/rooms", "/messages", "/calls", "/conferences"}) {
            assertTrue(paths.stream().anyMatch(p -> p.startsWith(domain)), domain);
        }
        assertTrue(paths.stream().noneMatch(p -> p.matches(".*(?:server|channel|folder|discover).*")), paths.toString());
    }

    @Test void legacyClassesAndCommonEntityAssociationsAreAbsent() {
        for (String name : new String[]{"model.Server", "model.Channel", "model.ChannelFolder",
                "model.ServerRole", "model.ServerMember", "model.ServerBan",
                "model.ServerMemberRole", "model.ChannelPermissionOverride", "model.FolderPermissionOverride",
                "service.PermissionService", "service.InviteCodeService"}) {
            assertThrows(ClassNotFoundException.class, () -> Class.forName("com.zvonok." + name), name);
        }
        assertThrows(NoSuchFieldException.class, () -> Message.class.getDeclaredField("channel"));
        assertThrows(NoSuchFieldException.class, () -> User.class.getDeclaredField("serverMemberships"));
    }
}
