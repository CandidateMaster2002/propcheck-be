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

import java.time.Instant;
import java.util.Map;

@Service
public class KekaTokenService {

    private static final Logger logger = LoggerFactory.getLogger(KekaTokenService.class);
    private static final String TOKEN_URL = "https://login.keka.com/connect/token";

    // Hardcoded Keka API credentials
    private static final String CLIENT_ID     = "04ef49f1-4f41-41cb-81cd-7bab54b32b27";
    private static final String CLIENT_SECRET = "Y7aAYqynTmBp5Vuf1hJF";
    private static final String API_KEY       = "ladDhy6EL5NYvoZ-Jp3K4aOZm6mFhoMkdbzldiS94VQ=";

    private final RestTemplate restTemplate;

    private String cachedToken;
    private Instant tokenExpiresAt;

    public KekaTokenService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public String getAccessToken() {
        if (cachedToken != null && Instant.now().isBefore(tokenExpiresAt)) {
            return cachedToken;
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "kekaapi");
        body.add("scope", "kekaapi");
        body.add("client_id", CLIENT_ID);
        body.add("client_secret", CLIENT_SECRET);
        body.add("api_key", API_KEY);

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(body, headers);
        ResponseEntity<Map> response = restTemplate.postForEntity(TOKEN_URL, request, Map.class);

        Map<String, Object> responseBody = response.getBody();
        if (responseBody == null || !responseBody.containsKey("access_token")) {
            throw new RuntimeException("Failed to obtain Keka access token");
        }

        cachedToken = (String) responseBody.get("access_token");
        // Keka tokens expire in 3600s; cache for 55 minutes to be safe
        int expiresIn = responseBody.get("expires_in") != null
                ? ((Number) responseBody.get("expires_in")).intValue() : 3600;
        tokenExpiresAt = Instant.now().plusSeconds(expiresIn - 300);

        logger.info("Keka access token obtained, expires in {}s", expiresIn);
        return cachedToken;
    }
}
