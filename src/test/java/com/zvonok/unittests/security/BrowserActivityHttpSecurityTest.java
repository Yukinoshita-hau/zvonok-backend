package com.zvonok.unittests.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zvonok.config.SecurityConfig;
import com.zvonok.controller.BrowserActivityController;
import com.zvonok.controller.BrowserActivityErrors;
import com.zvonok.exception_handler.GlobalExceptionHandler;
import com.zvonok.security.CustomAuthenticationEntryPoint;
import com.zvonok.security.JwtAuthenticationFilter;
import com.zvonok.security.JwtTokenProvider;
import com.zvonok.service.BrowserActivityService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class BrowserActivityHttpSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    BrowserActivityService activities;
    String bearer;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", Map.of(
                "security.allowedOrigins", "http://localhost", "browser.activity.enabled", "true",
                "app.jwt.ExpirationMs", "60000", "app.jwt.secret", Base64.getEncoder().encodeToString(new byte[64]))));
        context.register(Config.class); context.refresh();
        activities = context.getBean(BrowserActivityService.class);
        bearer = "Bearer " + context.getBean(JwtTokenProvider.class).generateToken("alice", 1L);
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void anonymousAndForgedTokenCannotCreateOrJoin() throws Exception {
        mvc.perform(post("/calls/7/browser-activity").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/calls/7/browser-activity").header("Authorization", "Bearer DEV_TOKEN"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/calls/7/browser-activity/" + UUID.randomUUID() + "/commands")
                .contentType(MediaType.APPLICATION_JSON).content("{\"op\":\"join\"}"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(activities);
    }
    @Test void derivesIdentityOnlyFromValidatedJwtAndDoesNotCacheViewerCredentials() throws Exception {
        mvc.perform(post("/calls/7/browser-activity").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"ownerId\":2,\"userId\":2,\"role\":\"owner\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"));
        verify(activities).create(7L, "alice");
        var id = UUID.randomUUID();
        mvc.perform(post("/calls/7/browser-activity/" + id + "/commands").header("Authorization", bearer)
                .contentType(MediaType.APPLICATION_JSON).content("{\"op\":\"join\"}"))
                .andExpect(status().isOk());
        verify(activities).command(7L, id, "alice", "join", null, null, null);
    }
    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, BrowserActivityController.class, BrowserActivityErrors.class, GlobalExceptionHandler.class})
    static class Config {
        @Bean BrowserActivityService activities() { return mock(BrowserActivityService.class); }
        @Bean ObjectMapper mapper() { return new ObjectMapper().configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); }
        @Bean JwtTokenProvider tokens() { var p = new JwtTokenProvider(); p.setJwtSecret(Base64.getEncoder().encodeToString(new byte[64])); p.setJwtExpirationMs(60000); return p; }
        @Bean JwtAuthenticationFilter filter(JwtTokenProvider p) { return new JwtAuthenticationFilter(p); }
        @Bean CustomAuthenticationEntryPoint entry(ObjectMapper m) { return new CustomAuthenticationEntryPoint(m); }
    }
}
