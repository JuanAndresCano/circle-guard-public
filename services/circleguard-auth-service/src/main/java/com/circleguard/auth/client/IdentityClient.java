package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import java.util.*;

@Component
public class IdentityClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${identity.service.url:http://localhost:8083}")
    private String identityServiceUrl;

    public IdentityClient(@Value("${identity.service.url}") String identityServiceUrl) {
        this.identityServiceUrl = identityServiceUrl;
    }

    public UUID getAnonymousId(String realIdentity) {
        Map<String, String> request = Map.of("realIdentity", realIdentity);
        String url = identityServiceUrl + "/api/v1/identities/map";

        try {
            Map response = restTemplate.postForObject(url, request, Map.class);

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