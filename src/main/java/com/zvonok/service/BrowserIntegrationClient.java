package com.zvonok.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "browser.activity.enabled", havingValue = "true")
public class BrowserIntegrationClient {
    private final ObjectMapper mapper;
    private final String key;
    private final URI endpoint;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    public BrowserIntegrationClient(ObjectMapper mapper,
            @Value("${browser.activity.key}") String key,
            @Value("${browser.activity.url:http://127.0.0.1:48035/integration}") String url) {
        // Only the integration API joins the trusted Core network; the Worker never does.
        if (key.length() < 43 || !(url.equals("http://127.0.0.1:48035/integration")
                || url.equals("http://browser-api:3000/integration")))
            throw new IllegalArgumentException("Browser Activity requires a strong key and an approved integration endpoint");
        this.mapper = mapper;
        this.key = key;
        this.endpoint = URI.create(url);
    }

    public JsonNode command(UUID activity, String subject, String op, JsonNode body, String epoch, String generation) {
        try {
            var claims = new LinkedHashMap<String, Object>();
            claims.put("aud", "zvonok-browser");
            claims.put("exp", System.currentTimeMillis() + 10000);
            claims.put("nonce", UUID.randomUUID().toString());
            claims.put("activityId", activity.toString());
            claims.put("subject", subject);
            claims.put("op", op);
            claims.put("body", body == null ? mapper.createObjectNode() : body);
            claims.put("epoch", epoch == null ? "" : epoch);
            claims.put("generation", generation == null ? "" : generation);
            var encoder = Base64.getUrlEncoder().withoutPadding();
            String payload = encoder.encodeToString(mapper.writeValueAsBytes(claims));
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String assertion = payload + "." + encoder.encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(20))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(java.util.Map.of("assertion", assertion)))).build();
            var response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                int status = switch (response.statusCode()) {
                    case 403, 429, 502 -> response.statusCode();
                    case 401 -> 503; // Integration credentials failing must not log the user out of Core.
                    default -> response.statusCode() >= 500 ? 503 : 409;
                };
                throw new ResponseStatusException(HttpStatus.valueOf(status), "Browser command rejected");
            }
            return mapper.readTree(response.body());
        } catch (ResponseStatusException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Browser unavailable");
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Browser unavailable");
        }
    }
}
