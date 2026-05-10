package com.circleguard.auth.client;

import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Component
public class IdentityClient {

    private final RestTemplate restTemplate = new RestTemplate();
    private static final String IDENTITY_URL = "http://localhost:8083/api/v1/identities/map";

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);

        try {
            Map response = restTemplate.postForObject(IDENTITY_URL, request, Map.class);

            if (response == null || response.get("anonymousId") == null) {
                throw new IllegalStateException("Identity service returned malformed response: missing 'anonymousId'");
            }

            return UUID.fromString(response.get("anonymousId").toString());

        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Identity service error: HTTP " + e.getStatusCode(), e);
        } catch (ResourceAccessException e) {
            throw new IllegalStateException("Identity service unreachable", e);
        }
    }
}