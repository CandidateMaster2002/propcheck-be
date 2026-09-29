package com.propchk.be.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Fetches a valid Zoho CRM access token using the client credentials / refresh-token flow.
 * Configure the four properties below in application-local.properties (never commit them).
 */
@Service
public class ZohoTokenService {

    private static final Logger logger = LoggerFactory.getLogger(ZohoTokenService.class);

    private static final String TOKEN_URL = "https://accounts.zoho.in/oauth/v2/token";

    @Value("${zoho.crm.client-id}")
    private String clientId;

    @Value("${zoho.crm.client-secret}")
    private String clientSecret;

    @Value("${zoho.crm.refresh-token}")
    private String refreshToken;

    private final RestTemplate restTemplate;

    public ZohoTokenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Exchanges the refresh token for a fresh access token every time it is called.
     * Zoho access tokens are short-lived (~1 hour); call this immediately before each API request.
     */
    @SuppressWarnings("unchecked")
    public String getAccessToken() {
        logger.info("Zoho config check - clientId length: " + clientId.length() + ", clientSecret length: " + clientSecret.length() + ", refreshToken length: " + refreshToken.length() + " | clientId starts with: " + clientId.substring(0, Math.min(10, clientId.length())));
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "refresh_token");
        body.add("client_id", clientId);
        body.add("client_secret", clientSecret);
        body.add("refresh_token", refreshToken);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URL, request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain Zoho access token: " + responseBody);
        }

        String token = (String) responseBody.get("access_token");
        logger.debug("Obtained fresh Zoho access token.");
        return token;
    }
}
