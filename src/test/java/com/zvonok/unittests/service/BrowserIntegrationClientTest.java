package com.zvonok.unittests.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.zvonok.service.BrowserIntegrationClient;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrowserIntegrationClientTest {
    @Test
    void mintedAssertionLeavesClockMarginWithinTheUnchangedApiValidationWindow() throws Exception {
        var remaining = new AtomicLong();
        var mapper = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/integration", exchange -> {
            var assertion = mapper.readTree(exchange.getRequestBody()).get("assertion").asText();
            var claims = mapper.readTree(Base64.getUrlDecoder().decode(assertion.split("\\.")[0]));
            remaining.set(claims.get("exp").asLong() - System.currentTimeMillis());
            byte[] body = "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            var client = new BrowserIntegrationClient(mapper, "a".repeat(43), "http://127.0.0.1:48035/integration");
            ReflectionTestUtils.setField(client, "endpoint", URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/integration"));
            client.command(UUID.randomUUID(), "1", "keepalive", null, null, null);
            assertTrue(remaining.get() > 0 && remaining.get() <= 8000, "Leave two seconds inside the API ten-second upper bound");
        } finally { server.stop(0); }
    }
    @Test
    void upstreamFailuresKeepTheUserFacingErrorContract() throws Exception {
        var status = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/integration", exchange -> {
            exchange.getRequestBody().readAllBytes();
            exchange.sendResponseHeaders(status.get(), -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new BrowserIntegrationClient(new ObjectMapper(), "a".repeat(43),
                    "http://127.0.0.1:48035/integration");
            ReflectionTestUtils.setField(client, "endpoint",
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/integration"));
            for (var contract : Map.of(400, 409, 401, 503, 403, 403, 404, 409,
                    409, 409, 429, 429, 500, 503, 502, 502, 503, 503, 504, 503).entrySet()) {
                status.set(contract.getKey());
                var error = assertThrows(ResponseStatusException.class,
                        () -> client.command(UUID.randomUUID(), "1", "navigate", null, null, null));
                assertEquals(contract.getValue().intValue(), error.getStatusCode().value(),
                        "Upstream HTTP " + contract.getKey());
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void onlyExplicitIntegrationEndpointsAndStrongKeysAreAccepted() {
        var mapper = new ObjectMapper();
        String key = "a".repeat(43);
        for (String url : List.of("http://127.0.0.1:48035/integration", "http://browser-api:3000/integration")) {
            assertDoesNotThrow(() -> new BrowserIntegrationClient(mapper, key, url));
            assertThrows(IllegalArgumentException.class, () -> new BrowserIntegrationClient(mapper, "short", url));
        }
        for (String url : List.of("http://169.254.169.254/integration", "http://browser-api:3000/integration?x=1",
                "http://browser-api:3000/integration/", "http://browser-api:3000@evil.example/integration",
                "http://browser-api.evil.example:3000/integration", "https://public.example/integration")) {
            assertThrows(IllegalArgumentException.class, () -> new BrowserIntegrationClient(mapper, key, url));
        }
    }
}
