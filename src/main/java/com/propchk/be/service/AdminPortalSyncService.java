package com.propchk.be.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.propchk.be.entity.Booking;
import com.propchk.be.entity.Lead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Service to dispatch two-way status updates from PropCheck back to the External Admin Portal.
 */
@Service
public class AdminPortalSyncService {

    private static final Logger log = LoggerFactory.getLogger(AdminPortalSyncService.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${admin.portal.webhook.url:}")
    private String adminPortalWebhookUrl;

    public AdminPortalSyncService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    /**
     * Notify Admin Portal that a new booking has been created/scheduled.
     */
    public void notifyBookingCreated(Lead lead, Booking booking) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "BOOKING_CREATED");
            payload.put("leadId", lead != null ? lead.getId() : null);
            payload.put("bookingId", booking != null ? booking.getId() : null);
            payload.put("phone", lead != null ? lead.getPhone() : null);
            payload.put("email", lead != null ? lead.getEmail() : null);
            payload.put("customerName", lead != null ? lead.getCustomerName() : null);
            payload.put("city", booking != null ? booking.getCity() : (lead != null ? lead.getCity() : null));
            payload.put("projectName", lead != null ? lead.getProjectName() : null);
            payload.put("unitNo", lead != null ? lead.getFlatNo() : null);
            payload.put("bhkType", lead != null ? lead.getBhkType() : null);
            payload.put("inspectionDate", booking != null && booking.getDate() != null ? booking.getDate().toString() : null);
            payload.put("slotTime", booking != null ? booking.getSlotTime() : null);
            payload.put("slot", booking != null ? booking.getSlot() : null);
            payload.put("inspectionCost", lead != null ? lead.getInspectionCost() : null);

            sendWebhook(payload);
        } catch (Exception e) {
            log.warn("Failed to notify Admin Portal of BOOKING_CREATED: {}", e.getMessage());
        }
    }

    /**
     * Notify Admin Portal that an engineer has been assigned to the booking.
     */
    public void notifyEngineerAssigned(Lead lead, Booking booking, com.propchk.be.entity.Engineer engineer) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "ENGINEER_ASSIGNED");
            payload.put("leadId", lead != null ? lead.getId() : null);
            payload.put("bookingId", booking != null ? booking.getId() : null);
            payload.put("phone", lead != null ? lead.getPhone() : null);
            payload.put("email", lead != null ? lead.getEmail() : null);
            payload.put("engineerName", engineer != null ? engineer.getName() : null);
            payload.put("engineerEmail", engineer != null ? engineer.getEmail() : null);
            payload.put("engineerNumber", engineer != null ? engineer.getEmployeeNumber() : null);

            sendWebhook(payload);
        } catch (Exception e) {
            log.warn("Failed to notify Admin Portal of ENGINEER_ASSIGNED: {}", e.getMessage());
        }
    }

    /**
     * Notify Admin Portal that a booking has been postponed.
     */
    public void notifyBookingPostponed(Lead lead, Booking booking, String reason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "POSTPONED");
            payload.put("leadId", lead != null ? lead.getId() : null);
            payload.put("bookingId", booking != null ? booking.getId() : null);
            payload.put("phone", lead != null ? lead.getPhone() : null);
            payload.put("email", lead != null ? lead.getEmail() : null);
            payload.put("postponeReason", reason);

            sendWebhook(payload);
        } catch (Exception e) {
            log.warn("Failed to notify Admin Portal of POSTPONED: {}", e.getMessage());
        }
    }

    /**
     * Notify Admin Portal that a booking has been cancelled.
     */
    public void notifyBookingCancelled(Lead lead, Booking booking, String reason) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "CANCELLED");
            payload.put("leadId", lead != null ? lead.getId() : null);
            payload.put("bookingId", booking != null ? booking.getId() : null);
            payload.put("phone", lead != null ? lead.getPhone() : null);
            payload.put("email", lead != null ? lead.getEmail() : null);
            payload.put("cancelReason", reason);

            sendWebhook(payload);
        } catch (Exception e) {
            log.warn("Failed to notify Admin Portal of CANCELLED: {}", e.getMessage());
        }
    }

    /**
     * Notify Admin Portal that the inspection report was reviewed and marked as VALIDATION_DONE.
     */
    public void notifyValidationDone(Lead lead, Booking booking, String remarks) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "VALIDATION_DONE");
            payload.put("leadId", lead != null ? lead.getId() : (booking != null ? booking.getLeadId() : null));
            payload.put("bookingId", booking != null ? booking.getId() : null);
            payload.put("phone", lead != null ? lead.getPhone() : null);
            payload.put("email", lead != null ? lead.getEmail() : null);
            payload.put("validatorRemarks", remarks != null ? remarks : (booking != null ? booking.getValidatorRemarks() : null));
            payload.put("validatorName", lead != null && lead.getValidatorName() != null ? lead.getValidatorName() : "PropCheck Quality Validator");
            payload.put("validatedAt", booking != null && booking.getValidatedAt() != null ? booking.getValidatedAt().toString() : Instant.now().toString());

            sendWebhook(payload);
        } catch (Exception e) {
            log.warn("Failed to prepare VALIDATION_DONE sync for Admin Portal: {}", e.getMessage());
        }
    }

    /**
     * Notify Admin Portal that the final report was sent to customer.
     */
    public void notifyReportSent(Lead lead, Booking booking, String reportUrl) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "REPORT_SENT");
            payload.put("leadId", lead != null ? lead.getId() : (booking != null ? booking.getLeadId() : null));
            payload.put("bookingId", booking != null ? booking.getId() : null);
            payload.put("phone", lead != null ? lead.getPhone() : null);
            payload.put("email", lead != null ? lead.getEmail() : null);
            payload.put("reportUrl", reportUrl);
            payload.put("sentAt", Instant.now().toString());

            sendWebhook(payload);
        } catch (Exception e) {
            log.warn("Failed to prepare REPORT_SENT sync for Admin Portal: {}", e.getMessage());
        }
    }

    private void sendWebhook(Map<String, Object> payload) {
        if (adminPortalWebhookUrl == null || adminPortalWebhookUrl.trim().isEmpty()) {
            log.info("Admin Portal webhook sync skipped: admin.portal.webhook.url is not configured.");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                String json = objectMapper.writeValueAsString(payload);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(adminPortalWebhookUrl.trim()))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() >= 200 && response.statusCode() < 300) {
                    log.info("Successfully synced event '{}' to Admin Portal (HTTP {})", payload.get("event"), response.statusCode());
                } else {
                    log.warn("Admin Portal sync returned HTTP {}: {}", response.statusCode(), response.body());
                }
            } catch (Exception e) {
                log.warn("Could not reach Admin Portal webhook at {}: {}", adminPortalWebhookUrl, e.getMessage());
            }
        });
    }
}
