package com.zvonok.unittests.service;

import com.sun.net.httpserver.HttpServer;
import com.zvonok.exception.LiveKitRoomSyncException;
import com.zvonok.service.LiveKitRoomAdminService;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.*;
import livekit.LivekitRoom;
import livekit.LivekitModels;
import static org.junit.jupiter.api.Assertions.*;

/** Real installed SDK and loopback HTTP; fixture responses, no SFU/media transport. */
class LiveKitPresenceHttpTest {
    HttpServer server;
    LiveKitRoomAdminService media;
    int status;
    String body;
    byte[] protobufBody;
    @BeforeEach void setup() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] bytes = status == 200 ? protobufBody : body.getBytes(StandardCharsets.UTF_8);
            exchange.getRequestBody().readAllBytes();
            exchange.getResponseHeaders().add("Content-Type", status == 200 ? "application/protobuf" : "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        media = new LiveKitRoomAdminService("test", "test-secret-for-local-http-fixtures-only",
                "http://127.0.0.1:" + server.getAddress().getPort());
    }
    @AfterEach void close() { server.stop(0); }
    @Test void emptySuccessfulResponseIsEvidence() {
        status = 200; protobufBody = LivekitRoom.ListParticipantsResponse.getDefaultInstance().toByteArray();
        assertTrue(media.listParticipants("call-3").isEmpty());
    }
    @Test void liveParticipantIsReturnedByInstalledSdk() {
        status = 200; protobufBody = LivekitRoom.ListParticipantsResponse.newBuilder()
                .addParticipants(LivekitModels.ParticipantInfo.newBuilder().setIdentity("alice")).build().toByteArray();
        assertEquals("alice", media.listParticipants("call-3").getFirst().getIdentity());
    }
    @Test void twirpMissingRoomIsEvidence() {
        status = 404; body = "{\"code\":\"not_found\",\"msg\":\"room not found\"}";
        assertTrue(media.listParticipants("call-3").isEmpty());
    }
    @Test void serverFailureIsNotAbsenceEvenIfMessageContainsNotFound() {
        status = 500; body = "{\"code\":\"not_found\",\"msg\":\"upstream not found\"}";
        assertThrows(LiveKitRoomSyncException.class, () -> media.listParticipants("call-3"));
    }
    @Test void proxy404IsNotAbsence() {
        status = 404; body = "<html>404 not found</html>";
        assertThrows(LiveKitRoomSyncException.class, () -> media.listParticipants("call-3"));
    }
}
