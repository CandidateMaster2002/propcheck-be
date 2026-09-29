package com.propchk.be.controller;

import com.propchk.be.entity.Lead;
import com.propchk.be.service.ZohoLeadMapper;
import com.propchk.be.service.ZohoTokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Receives outbound webhook notifications from Zoho CRM Workflow Rules.
 *
 * SECURITY NOTE: This endpoint has no authentication intentionally.
 * Zoho's webhook runner does not send our JWT/session cookie, so we
 * cannot use the standard auth filter here. A future hardening step
 * should add HMAC signature verification using a shared secret stored
 * in application-local.properties once Zoho supports signing on webhook alerts.
 */
@RestController
@RequestMapping("/api/webhooks/zoho")
public class ZohoWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(ZohoWebhookController.class);

    private final ZohoTokenService zohoTokenService;
    private final ZohoLeadMapper leadMapper;
    private final RestTemplate restTemplate;

    public ZohoWebhookController(ZohoTokenService zohoTokenService,
                                  ZohoLeadMapper leadMapper,
                                  RestTemplate restTemplate) {
        this.zohoTokenService = zohoTokenService;
        this.leadMapper = leadMapper;
        this.restTemplate = restTemplate;
    }

    /**
     * POST /api/webhooks/zoho/lead-update
     *
     * Triggered by a Zoho CRM Workflow Rule whenever a Lead record changes.
     * Only acts when Lead_Status == "Lead Won"; ignores all other edits.
     *
     * Flow:
     *  1. Log the raw payload so we can inspect its real shape on the first trigger.
     *  2. Extract the record ID from the payload (Zoho's shape varies by config).
     *  3. Fetch the full lead record from Zoho CRM using that ID.
     *  4. If Lead_Status != "Lead Won", return 200 and do nothing.
     *  5. Upsert the lead using ZohoLeadMapper (shared with bulk-sync).
     *  6. Return 200 OK.
     *
     * Always returns 200 OK — a non-200 response would cause Zoho to
     * disable the webhook after repeated failures.
     */
    @PostMapping("/lead-update")
    @SuppressWarnings("unchecked")
    public ResponseEntity<Map<String, Object>> handleLeadUpdate(
            @RequestBody Map<String, Object> payload) {

        // 1. Log the raw payload in full so we can inspect the real Zoho shape
        logger.info("=== Zoho Webhook Received ===");
        logger.info("Raw payload: {}", payload);

        // 2. Extract the Zoho record ID — Zoho's webhook format varies by configuration.
        //    Try common locations in priority order:
        //      (a) top-level "id"                 → most webhook alert configs
        //      (b) top-level "Id"                 → some configs capitalise it
        //      (c) nested under record -> id      → some notification formats
        //      (d) nested under data[0] -> id     → bulk-change notification format
        String zohoId = extractZohoId(payload);

        if (zohoId == null) {
            logger.warn("Could not extract Zoho record ID from webhook payload. " +
                    "Returning 200 OK to prevent Zoho from disabling this webhook. " +
                    "Check the raw payload logged above to identify the correct field path.");
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "no_id_found"));
        }

        logger.info("Extracted Zoho lead ID: {}", zohoId);

        // 3. Fetch the full record from Zoho CRM (webhooks carry minimal data;
        //    fetching the full record guarantees we have the complete, current field set)
        Map<String, Object> fullRecord;
        try {
            fullRecord = fetchFullLeadRecord(zohoId);
        } catch (Exception ex) {
            // Log and swallow so Zoho doesn't disable the webhook
            logger.error("Failed to fetch full lead record {} from Zoho CRM: {}", zohoId, ex.getMessage(), ex);
            return ResponseEntity.ok(Map.of("status", "error", "reason", "crm_fetch_failed"));
        }

        if (fullRecord == null) {
            logger.warn("Zoho CRM returned no record for id {}. Skipping.", zohoId);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "record_not_found_in_crm"));
        }

        logger.info("Fetched full record for id {}. Fields present: {}", zohoId, fullRecord.keySet());

        // 4. Only process "Lead Won" records — ignore every other edit
        Object leadStatus = fullRecord.get("Lead_Status");
        if (!"Lead Won".equals(leadStatus)) {
            logger.info("Lead {} has status '{}', not 'Lead Won'. Ignoring.", zohoId, leadStatus);
            return ResponseEntity.ok(Map.of("status", "ignored", "reason", "not_lead_won"));
        }

        // 5. Upsert into our DB using the shared mapper
        Lead saved = leadMapper.mapAndSave(fullRecord);
        if (saved == null) {
            logger.warn("Mapper returned null for zohoId {}. Check mapper logs.", zohoId);
            return ResponseEntity.ok(Map.of("status", "error", "reason", "mapper_returned_null"));
        }

        logger.info("Upserted Lead Won record: localId={} zohoId={} customer={}",
                saved.getId(), zohoId, saved.getCustomerName());

        // 6. Done
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", "ok");
        response.put("zohoId", zohoId);
        response.put("localLeadId", saved.getId());
        return ResponseEntity.ok(response);
    }

    // ─── private helpers ─────────────────────────────────────────────────────

    /**
     * Try several locations where Zoho may embed the record ID in a webhook payload.
     * Returns null if none are found, so the caller can log + return 200 safely.
     */
    private String extractZohoId(Map<String, Object> payload) {
        // (a) top-level "id"
        if (payload.containsKey("id") && payload.get("id") != null) {
            return payload.get("id").toString();
        }
        // (b) top-level "Id" (capitalised)
        if (payload.containsKey("Id") && payload.get("Id") != null) {
            return payload.get("Id").toString();
        }
        // (c) nested under "record" -> "id"
        if (payload.get("record") instanceof Map) {
            Map<?, ?> record = (Map<?, ?>) payload.get("record");
            if (record.get("id") != null) return record.get("id").toString();
            if (record.get("Id") != null) return record.get("Id").toString();
        }
        // (d) nested under "data" (array) -> first element -> "id"
        if (payload.get("data") instanceof List) {
            List<?> data = (List<?>) payload.get("data");
            if (!data.isEmpty() && data.get(0) instanceof Map) {
                Map<?, ?> first = (Map<?, ?>) data.get(0);
                if (first.get("id") != null) return first.get("id").toString();
                if (first.get("Id") != null) return first.get("Id").toString();
            }
        }
        return null;
    }

    /**
     * Calls GET /crm/v6/Leads/{id} with a fresh token and returns the record map,
     * or null if the API returns no data.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> fetchFullLeadRecord(String zohoId) {
        String url = "https://www.zohoapis.in/crm/v6/Leads/" + zohoId;
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + zohoTokenService.getAccessToken());
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, entity, Map.class);
        Map<String, Object> body = response.getBody();

        if (body == null || !body.containsKey("data")) return null;

        List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
        return (data != null && !data.isEmpty()) ? data.get(0) : null;
    }
}
