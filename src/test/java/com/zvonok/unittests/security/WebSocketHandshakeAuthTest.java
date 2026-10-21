package com.zvonok.unittests.security;

import com.zvonok.handler.JwtHandshakeInterceptor;
import com.zvonok.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;

import java.net.URI;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketHandshakeAuthTest {
    private final JwtTokenProvider tokens = mock(JwtTokenProvider.class);
    private final JwtHandshakeInterceptor handshake = new JwtHandshakeInterceptor(tokens);
    private final ServerHttpRequest request = mock(ServerHttpRequest.class);
    private final ServerHttpResponse response = mock(ServerHttpResponse.class);
    private final HttpHeaders headers = new HttpHeaders();

    @BeforeEach void prepare() {
        when(request.getHeaders()).thenReturn(headers);
        when(response.getHeaders()).thenReturn(new HttpHeaders());
        when(tokens.isValidToken("signed-token")).thenReturn(true);
        when(tokens.getUsername("signed-token")).thenReturn("alice");
    }

    @Test void browserAuthenticationOfferPreservesPrincipalAndToken() throws Exception {
        headers.add("Sec-WebSocket-Protocol", "v12.stomp, v11.stomp, zvonok.auth.signed-token");
        var attributes = new HashMap<String, Object>();
        assertTrue(handshake.beforeHandshake(request, response, null, attributes));
        assertEquals("alice", attributes.get("username"));
        assertEquals("signed-token", attributes.get("token"));
    }

    @Test void invalidBrowserOfferIsDenied() throws Exception {
        headers.add("Sec-WebSocket-Protocol", "v12.stomp, zvonok.auth.expired");
        assertFalse(handshake.beforeHandshake(request, response, null, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
    }

    @Test void queryTokenCannotAuthenticate() throws Exception {
        when(request.getURI()).thenReturn(URI.create("http://localhost/api/ws-raw?token=signed-token"));
        assertFalse(handshake.beforeHandshake(request, response, null, new HashMap<>()));
        verify(response).setStatusCode(HttpStatus.UNAUTHORIZED);
        verify(tokens, never()).isValidToken("signed-token");
    }

    @Test void nativeAuthorizationHeaderStillWorks() throws Exception {
        headers.setBearerAuth("signed-token");
        assertTrue(handshake.beforeHandshake(request, response, null, new HashMap<>()));
    }
}
