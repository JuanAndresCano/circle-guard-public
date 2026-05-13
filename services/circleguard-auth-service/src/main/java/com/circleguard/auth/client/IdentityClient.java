package com.circleguard.auth.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@Component
public class IdentityClient {

    private final RestTemplate restTemplate;

    @Value("${identity.service.url}")
    private String identityServiceUrl;

    public IdentityClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public UUID getAnonymousId(String realIdentity) {

        try {

            Map<String, String> request =
                    Map.of("realIdentity", realIdentity);

            Map response = restTemplate.postForObject(
                    identityServiceUrl,
                    request,
                    Map.class
            );

            if (response == null || !response.containsKey("anonymousId")) {
                throw new IllegalStateException(
                        "Identity service returned malformed response"
                );
            }

            return UUID.fromString(
                    response.get("anonymousId").toString()
            );

        } catch (HttpStatusCodeException ex) {

            throw new IllegalStateException(
                    "Identity service error: HTTP " + ex.getStatusCode(),
                    ex
            );

        } catch (ResourceAccessException ex) {

            throw new IllegalStateException(
                    "Identity service unavailable",
                    ex
            );

        } catch (Exception ex) {

            throw new IllegalStateException(
                    "Unexpected identity service error",
                    ex
            );
        }
    }
}