package com.propchk.be.service;

import com.propchk.be.entity.Lead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class TeamsNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(TeamsNotificationService.class);

    @Value("${teams.webhook.url:}")
    private String teamsWebhookUrl;

    @Value("${portal.frontend.url:https://propchck.netlify.app}")
    private String portalFrontendUrl;

    @Value("${app.backend.base-url:https://propcheck-be.onrender.com}")
    private String backendBaseUrl;

    private final RestTemplate restTemplate;

    public TeamsNotificationService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Sends an interactive Adaptive Card to the configured MS Teams channel
     * whenever a Lead is marked as "Lead Won".
     */
    public void sendLeadWonNotification(Lead lead) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) {
            logger.warn("Teams Webhook URL is not configured. Skipping Teams notification.");
            return;
        }

        try {
            Map<String, Object> payload = buildAdaptiveCard(lead);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);

            restTemplate.postForEntity(teamsWebhookUrl, request, String.class);
            logger.info("Successfully sent Teams notification for Lead Won: {}", lead.getCustomerName());
        } catch (Exception e) {
            // Log warning but don't fail the primary flow if Teams is temporarily unreachable
            logger.error("Failed to post notification to Teams channel: {}", e.getMessage(), e);
        }
    }

    private Map<String, Object> buildAdaptiveCard(Lead lead) {
        String customer = lead.getCustomerName() != null ? lead.getCustomerName() : "Customer";
        String city = lead.getCity() != null ? lead.getCity() : "N/A";
        String project = lead.getProjectName() != null ? lead.getProjectName() : "N/A";
        String plan = lead.getInspectionType() != null ? lead.getInspectionType() : "N/A";
        String payment = lead.getPaymentStatus() != null ? lead.getPaymentStatus() : "Unpaid";
        String owner = lead.getLeadOwner() != null ? lead.getLeadOwner() : "N/A";
        String phone = lead.getPhone() != null ? lead.getPhone() : "N/A";
        String zohoId = lead.getZohoLeadId() != null ? lead.getZohoLeadId() : "";

        // Build facts
        List<Map<String, String>> facts = new ArrayList<>();
        facts.add(Map.of("title", "Customer", "value", customer));
        facts.add(Map.of("title", "Phone", "value", phone));
        facts.add(Map.of("title", "City", "value", city));
        facts.add(Map.of("title", "Project", "value", project));
        facts.add(Map.of("title", "Plan", "value", plan));
        facts.add(Map.of("title", "Payment Status", "value", payment));
        facts.add(Map.of("title", "Lead Owner", "value", owner));

        // Card body with sleek container header
        List<Map<String, Object>> body = new ArrayList<>();
        body.add(Map.of(
                "type", "Container",
                "style", "emphasis",
                "bleed", true,
                "items", List.of(
                        Map.of(
                                "type", "ColumnSet",
                                "columns", List.of(
                                        Map.of(
                                                "type", "Column",
                                                "width", "stretch",
                                                "items", List.of(
                                                        Map.of(
                                                                "type", "TextBlock",
                                                                "size", "Large",
                                                                "weight", "Bolder",
                                                                "text", "NEW LEAD: " + customer,
                                                                "color", "Good"
                                                        ),
                                                        Map.of(
                                                                "type", "TextBlock",
                                                                "spacing", "None",
                                                                "text", "Deal Closed in Zoho CRM • Ready to Schedule",
                                                                "isSubtle", true
                                                        )
                                                )
                                        ),
                                        Map.of(
                                                "type", "Column",
                                                "width", "auto",
                                                "items", List.of(
                                                        Map.of(
                                                                "type", "TextBlock",
                                                                "text", payment.toUpperCase(),
                                                                "weight", "Bolder",
                                                                "color", "Fully Paid".equalsIgnoreCase(payment) ? "Good" : "Warning"
                                                        )
                                                )
                                        )
                                )
                        )
                )
        ));

        body.add(Map.of(
                "type", "Container",
                "items", List.of(
                        Map.of(
                                "type", "FactSet",
                                "facts", facts
                        )
                )
        ));

        // Actions (Buttons)
        List<Map<String, Object>> actions = new ArrayList<>();
        String bookingPortalUrl = (lead.getId() != null)
                ? portalFrontendUrl.replaceAll("/$", "") + "/leads/" + lead.getId()
                : portalFrontendUrl;

        actions.add(Map.of(
                "type", "Action.OpenUrl",
                "title", "Book in Portal",
                "style", "positive",
                "url", bookingPortalUrl
        ));

        if (!zohoId.isEmpty()) {
            actions.add(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "Open in Zoho CRM",
                    "url", "https://crm.zoho.in/crm/org60015112021/tab/Leads/" + zohoId
            ));
        }

        // Adaptive Card content
        Map<String, Object> cardContent = new LinkedHashMap<>();
        cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        cardContent.put("type", "AdaptiveCard");
        cardContent.put("version", "1.4");
        cardContent.put("body", body);
        cardContent.put("actions", actions);

        // Teams wrapper format
        Map<String, Object> attachment = new LinkedHashMap<>();
        attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
        attachment.put("content", cardContent);

        Map<String, Object> outer = new LinkedHashMap<>();
        outer.put("type", "message");
        outer.put("attachments", List.of(attachment));

        return outer;
    }

    /**
     * Sends an urgent red Adaptive Card alert when an engineer's approved leave
     * clashes with a scheduled customer inspection.
     */
    public void sendConflictAlertNotification(com.propchk.be.entity.Booking booking,
                                              Lead lead,
                                              String engineerName,
                                              String leaveType,
                                              String reason) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "N/A";
            String city = booking.getCity() != null ? booking.getCity() : (lead != null ? lead.getCity() : "N/A");
            String dateStr = booking.getDate() != null ? booking.getDate().toString() : "N/A";
            String slotStr = (booking.getSlotTime() != null ? booking.getSlotTime() + " " : "")
                    + (booking.getSlot() != null ? "(" + booking.getSlot() + ")" : "");

            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Date & Slot", "value", dateStr + " • " + slotStr));
            facts.add(Map.of("title", "Project / City", "value", project + " (" + city + ")"));
            facts.add(Map.of("title", "Assigned Engineer", "value", engineerName != null ? engineerName : booking.getEngineerEmployeeNumber()));
            facts.add(Map.of("title", "Leave Type", "value", leaveType != null ? leaveType : "Approved Leave"));
            facts.add(Map.of("title", "Conflict Detail", "value", reason != null ? reason : "Leave clashing with booking"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "🚨 URGENT: Inspection Conflict Detected!",
                    "color", "Attention" // Red color in Teams
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "An assigned engineer has an approved leave clashing with this confirmed inspection. Re-assignment is required.",
                    "wrap", true
            ));
            body.add(Map.of(
                    "type", "FactSet",
                    "facts", facts
            ));

            List<Map<String, Object>> actions = new ArrayList<>();
            String reassignUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) {
                reassignUrl += "/leads/" + lead.getId();
            }
            actions.add(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "🔘 Re-assign Engineer in Portal",
                    "url", reassignUrl
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
            attachment.put("content", cardContent);

            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("type", "message");
            outer.put("attachments", List.of(attachment));

            postToTeams(outer);
            logger.info("Sent Teams conflict alert for booking ID {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to send Teams conflict alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Sends a morning briefing Adaptive Card with today's scheduled inspections roster.
     */
    public void sendDailyBriefingNotification(java.time.LocalDate date,
                                              List<com.propchk.be.entity.Booking> bookings,
                                              Map<Long, Lead> leadMap,
                                              Map<String, String> engineerNames) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) {
            return;
        }

        try {
            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "☀️ Daily Inspection Briefing: " + date,
                    "color", "Accent" // Blue / primary color
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "Good morning team! Here is today's scheduled inspection roster across all cities.",
                    "wrap", true
            ));

            if (bookings.isEmpty()) {
                body.add(Map.of(
                        "type", "TextBlock",
                        "text", "ℹ️ *No inspections scheduled for today.* Engineers are available for bookings.",
                        "wrap", true
                ));
            } else {
                List<Map<String, String>> facts = new ArrayList<>();
                for (com.propchk.be.entity.Booking b : bookings) {
                    Lead lead = leadMap.get(b.getLeadId());
                    String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Lead #" + b.getLeadId();
                    String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "Project";
                    String eng = engineerNames.getOrDefault(b.getEngineerEmployeeNumber(), b.getEngineerEmployeeNumber() != null ? b.getEngineerEmployeeNumber() : "Unassigned");
                    String time = (b.getSlotTime() != null ? b.getSlotTime() : b.getSlot()) + " (" + b.getCity() + ")";

                    facts.add(Map.of(
                            "title", time,
                            "value", customer + " @ " + project + " • Eng: " + eng
                    ));
                }
                body.add(Map.of(
                        "type", "FactSet",
                        "facts", facts
                ));
            }

            List<Map<String, Object>> actions = new ArrayList<>();
            actions.add(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "📅 View Full Schedule Matrix",
                    "url", portalFrontendUrl.replaceAll("/$", "")
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
            attachment.put("content", cardContent);

            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("type", "message");
            outer.put("attachments", List.of(attachment));

            postToTeams(outer);
            logger.info("Sent Teams daily briefing for {}", date);
        } catch (Exception e) {
            logger.error("Failed to send Teams daily briefing: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 8: Sends an actionable card with 1-click engineer assignment buttons.
     */
    public void sendAssignEngineerCardNotification(com.propchk.be.entity.Booking booking,
                                                   Lead lead,
                                                   List<com.propchk.be.entity.Engineer> availableEngineers) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "N/A";
            String city = booking.getCity() != null ? booking.getCity() : "N/A";
            String dateStr = booking.getDate() != null ? booking.getDate().toString() : "N/A";
            String slotStr = (booking.getSlotTime() != null ? booking.getSlotTime() + " " : "")
                    + (booking.getSlot() != null ? "(" + booking.getSlot() + ")" : "");

            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Project / City", "value", project + " (" + city + ")"));
            facts.add(Map.of("title", "Date & Slot", "value", dateStr + " • " + slotStr));
            facts.add(Map.of("title", "Current Status", "value", "PENDING_ENGINEER_ASSIGNMENT"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "👷 1-Click Engineer Assignment: " + customer,
                    "color", "Accent"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "This booking is awaiting engineer assignment. Click an available engineer below to assign instantly from Teams:",
                    "wrap", true
            ));
            body.add(Map.of(
                    "type", "FactSet",
                    "facts", facts
            ));

            List<Map<String, Object>> actions = new ArrayList<>();
            // Create 1-click assignment button for up to 3 available engineers
            int count = 0;
            if (availableEngineers != null) {
                for (com.propchk.be.entity.Engineer eng : availableEngineers) {
                    if (count >= 3) break;
                    String assignUrl = backendBaseUrl.replaceAll("/$", "")
                            + "/api/teams/actions/assign-engineer-redirect?bookingId=" + booking.getId()
                            + "&engineerEmp=" + eng.getEmployeeNumber();
                    actions.add(Map.of(
                            "type", "Action.OpenUrl",
                            "title", "⚡ Assign " + eng.getName(),
                            "url", assignUrl
                    ));
                    count++;
                }
            }

            // Fallback button to open portal
            String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) {
                portalLeadUrl += "/leads/" + lead.getId();
            }
            actions.add(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "🌐 Open in Portal",
                    "url", portalLeadUrl
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
            attachment.put("content", cardContent);

            Map<String, Object> outer = new LinkedHashMap<>();
            outer.put("type", "message");
            outer.put("attachments", List.of(attachment));

            postToTeams(outer);
            logger.info("Sent 1-click engineer assignment card for booking ID {}", booking.getId());
        } catch (Exception e) {
            logger.error("Failed to send 1-click engineer assignment card: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 8: Confirms assignment in Teams.
     */
    public void sendEngineerAssignedConfirmationNotification(com.propchk.be.entity.Booking booking,
                                                             Lead lead,
                                                             String engineerName,
                                                             String assignedBy) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "N/A";

            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Project", "value", project + " (" + booking.getCity() + ")"));
            facts.add(Map.of("title", "Date & Slot", "value", booking.getDate() + " • " + booking.getSlotTime()));
            facts.add(Map.of("title", "Assigned Engineer", "value", engineerName));
            facts.add(Map.of("title", "Assigned By", "value", assignedBy != null ? assignedBy : "City Head (Teams)"));
            facts.add(Map.of("title", "Status", "value", "CONFIRMED ✅"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "✅ Engineer Assigned: " + engineerName,
                    "color", "Good"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "The inspection is now **CONFIRMED** and synchronized with Zoho CRM.",
                    "wrap", true
            ));
            body.add(Map.of(
                    "type", "FactSet",
                    "facts", facts
            ));

            String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) portalLeadUrl += "/leads/" + lead.getId();

            List<Map<String, Object>> actions = List.of(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "📅 View Booking in Portal",
                    "url", portalLeadUrl
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
            attachment.put("content", cardContent);

            postToTeams(Map.of("type", "message", "attachments", List.of(attachment)));
        } catch (Exception e) {
            logger.error("Failed to send assignment confirmation: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 9: Sends a validation request card with 1-click approval.
     */
    public void sendValidationRequestNotification(com.propchk.be.entity.Booking booking,
                                                  Lead lead,
                                                  String engineerNotes) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "N/A";

            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Project", "value", project + " (" + booking.getCity() + ")"));
            facts.add(Map.of("title", "Inspection Date", "value", booking.getDate() != null ? booking.getDate().toString() : "N/A"));
            facts.add(Map.of("title", "Engineer Notes", "value", engineerNotes != null ? engineerNotes : "Inspection completed on-site. Snags recorded."));
            facts.add(Map.of("title", "Report Status", "value", "Inspection Done (Pending Validation)"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "🔍 Report Validation Required: " + customer,
                    "color", "Warning"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "The on-site engineer has completed the inspection. Please validate the report so it can be delivered to the client.",
                    "wrap", true
            ));
            body.add(Map.of(
                    "type", "FactSet",
                    "facts", facts
            ));

            String validateUrl = backendBaseUrl.replaceAll("/$", "")
                    + "/api/teams/actions/validate-report-redirect?bookingId=" + booking.getId();
            String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) portalLeadUrl += "/leads/" + lead.getId();

            List<Map<String, Object>> actions = List.of(
                    Map.of(
                            "type", "Action.OpenUrl",
                            "title", "✅ Validate & Release Report",
                            "url", validateUrl
                    ),
                    Map.of(
                            "type", "Action.OpenUrl",
                            "title", "📄 Review in Portal",
                            "url", portalLeadUrl
                    )
            );

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            Map<String, Object> attachment = new LinkedHashMap<>();
            attachment.put("contentType", "application/vnd.microsoft.card.adaptive");
            attachment.put("content", cardContent);

            postToTeams(Map.of("type", "message", "attachments", List.of(attachment)));
        } catch (Exception e) {
            logger.error("Failed to send validation request: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 9: Confirms validation in Teams.
     */
    public void sendValidationConfirmationNotification(com.propchk.be.entity.Booking booking,
                                                        Lead lead,
                                                        String validatedBy) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Validated By", "value", validatedBy != null ? validatedBy : "City Head (Teams)"));
            facts.add(Map.of("title", "New Status", "value", "VALIDATION_DONE 🎉"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "🎉 Report Validated: " + customer,
                    "color", "Good"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "The inspection report has been successfully validated and approved for sending.",
                    "wrap", true
            ));
            body.add(Map.of(
                    "type", "FactSet",
                    "facts", facts
            ));

            String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) portalLeadUrl += "/leads/" + lead.getId();

            List<Map<String, Object>> actions = List.of(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "📄 Open in Portal",
                    "url", portalLeadUrl
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            postToTeams(Map.of("type", "message", "attachments", List.of(Map.of(
                    "contentType", "application/vnd.microsoft.card.adaptive",
                    "content", cardContent
            ))));
        } catch (Exception e) {
            logger.error("Failed to send validation confirmation: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 2: Cancellation alert.
     */
    public void sendCancellationAlertNotification(com.propchk.be.entity.Booking booking,
                                                  Lead lead,
                                                  String cancelledBy) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "N/A";

            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Project / City", "value", project + " (" + booking.getCity() + ")"));
            facts.add(Map.of("title", "Cancelled Slot", "value", booking.getDate() + " • " + booking.getSlotTime()));
            facts.add(Map.of("title", "Cancelled By", "value", cancelledBy != null ? cancelledBy : "Customer / Sales"));
            facts.add(Map.of("title", "Current Status", "value", "CANCELLED"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "⚠️ Inspection Cancelled: " + customer,
                    "color", "Attention"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "The customer inspection booking has been cancelled. Slot has been freed for other bookings.",
                    "wrap", true
            ));
            body.add(Map.of("type", "FactSet", "facts", facts));

            String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) portalLeadUrl += "/leads/" + lead.getId();

            List<Map<String, Object>> actions = List.of(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "🔍 View Lead in Portal",
                    "url", portalLeadUrl
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            postToTeams(Map.of("type", "message", "attachments", List.of(Map.of(
                    "contentType", "application/vnd.microsoft.card.adaptive",
                    "content", cardContent
            ))));
        } catch (Exception e) {
            logger.error("Failed to send cancellation alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 2: Reschedule alert.
     */
    public void sendRescheduleAlertNotification(com.propchk.be.entity.Booking booking,
                                                Lead lead,
                                                java.time.LocalDate oldDate,
                                                java.time.LocalDate newDate,
                                                String requestedBy) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            String customer = (lead != null && lead.getCustomerName() != null) ? lead.getCustomerName() : "Customer";
            String project = (lead != null && lead.getProjectName() != null) ? lead.getProjectName() : "N/A";

            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Customer", "value", customer));
            facts.add(Map.of("title", "Project / City", "value", project + " (" + booking.getCity() + ")"));
            facts.add(Map.of("title", "Previous Date", "value", oldDate != null ? oldDate.toString() : "N/A"));
            facts.add(Map.of("title", "New Date & Slot", "value", newDate + " • " + booking.getSlotTime()));
            facts.add(Map.of("title", "Rescheduled By", "value", requestedBy != null ? requestedBy : "Customer / Sales"));
            facts.add(Map.of("title", "Status", "value", booking.getStatus() + " (Engineer Cleared)"));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "🔄 Inspection Rescheduled: " + customer,
                    "color", "Accent"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "The customer inspection has been moved to a new date. Previous engineer assignment was cleared and must be re-assigned.",
                    "wrap", true
            ));
            body.add(Map.of("type", "FactSet", "facts", facts));

            String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) portalLeadUrl += "/leads/" + lead.getId();

            List<Map<String, Object>> actions = List.of(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "👷 Allot Engineer for New Date",
                    "url", portalLeadUrl
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            postToTeams(Map.of("type", "message", "attachments", List.of(Map.of(
                    "contentType", "application/vnd.microsoft.card.adaptive",
                    "content", cardContent
            ))));
        } catch (Exception e) {
            logger.error("Failed to send reschedule alert: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 6: Weekly Management Digest.
     */
    public void sendWeeklyManagementDigestNotification(Map<String, Object> metrics) {
        if (teamsWebhookUrl == null || teamsWebhookUrl.trim().isEmpty()) return;

        try {
            List<Map<String, String>> facts = new ArrayList<>();
            facts.add(Map.of("title", "Completed Inspections (Last 7 Days)", "value", String.valueOf(metrics.getOrDefault("completedCount", 0))));
            facts.add(Map.of("title", "Upcoming Inspections (Next 7 Days)", "value", String.valueOf(metrics.getOrDefault("upcomingCount", 0))));
            facts.add(Map.of("title", "Active Conflicts Resolved", "value", String.valueOf(metrics.getOrDefault("conflictsResolved", 0))));
            facts.add(Map.of("title", "Top Performing City", "value", String.valueOf(metrics.getOrDefault("topCity", "Bangalore"))));

            List<Map<String, Object>> body = new ArrayList<>();
            body.add(Map.of(
                    "type", "TextBlock",
                    "size", "Large",
                    "weight", "Bolder",
                    "text", "📊 Executive Weekly Operations Digest",
                    "color", "Accent"
            ));
            body.add(Map.of(
                    "type", "TextBlock",
                    "text", "Here is the 7-day executive rollup of inspection velocity and engineer utilization:",
                    "wrap", true
            ));
            body.add(Map.of("type", "FactSet", "facts", facts));

            List<Map<String, Object>> actions = List.of(Map.of(
                    "type", "Action.OpenUrl",
                    "title", "📊 Open Management Analytics",
                    "url", portalFrontendUrl.replaceAll("/$", "")
            ));

            Map<String, Object> cardContent = new LinkedHashMap<>();
            cardContent.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
            cardContent.put("type", "AdaptiveCard");
            cardContent.put("version", "1.4");
            cardContent.put("body", body);
            cardContent.put("actions", actions);

            postToTeams(Map.of("type", "message", "attachments", List.of(Map.of(
                    "contentType", "application/vnd.microsoft.card.adaptive",
                    "content", cardContent
            ))));
            logger.info("Sent weekly management digest to Teams");
        } catch (Exception e) {
            logger.error("Failed to send weekly digest: {}", e.getMessage(), e);
        }
    }

    /**
     * Feature 14 & 15: Builds an Adaptive Card for lead detail search & bot query.
     */
    public Map<String, Object> buildLeadDetailCard(Lead lead, com.propchk.be.entity.Booking activeBooking) {
        String customer = lead.getCustomerName() != null ? lead.getCustomerName() : "Customer";
        String project = lead.getProjectName() != null ? lead.getProjectName() : "N/A";
        String city = lead.getCity() != null ? lead.getCity() : "N/A";
        String payment = lead.getPaymentStatus() != null ? lead.getPaymentStatus() : "Unpaid";
        String dateStr = (activeBooking != null && activeBooking.getDate() != null)
                ? activeBooking.getDate().toString() + " (" + activeBooking.getSlotTime() + ")"
                : (lead.getInspectionDateAndTime() != null ? lead.getInspectionDateAndTime() : "Unbooked");
        String bookingStatus = activeBooking != null ? activeBooking.getStatus() : "UNBOOKED";

        List<Map<String, String>> facts = new ArrayList<>();
        facts.add(Map.of("title", "Customer", "value", customer));
        facts.add(Map.of("title", "Phone", "value", lead.getPhone() != null ? lead.getPhone() : "N/A"));
        facts.add(Map.of("title", "Email", "value", lead.getEmail() != null ? lead.getEmail() : "N/A"));
        facts.add(Map.of("title", "Project / City", "value", project + " (" + city + ")"));
        facts.add(Map.of("title", "Inspection Slot", "value", dateStr));
        facts.add(Map.of("title", "Payment Status", "value", payment));
        facts.add(Map.of("title", "Booking Status", "value", bookingStatus));

        List<Map<String, Object>> body = new ArrayList<>();
        body.add(Map.of(
                "type", "TextBlock",
                "size", "Large",
                "weight", "Bolder",
                "text", "🔍 Lead Profile: " + customer,
                "color", "Accent"
        ));
        body.add(Map.of("type", "FactSet", "facts", facts));

        String portalLeadUrl = portalFrontendUrl.replaceAll("/$", "");
        if (lead.getId() != null) portalLeadUrl += "/leads/" + lead.getId();

        List<Map<String, Object>> actions = List.of(Map.of(
                "type", "Action.OpenUrl",
                "title", "🌐 Open Full Lead Details",
                "url", portalLeadUrl
        ));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", body);
        card.put("actions", actions);

        return card;
    }

    /**
     * Feature 15: Builds a capacity card for bot queries.
     */
    public Map<String, Object> buildCapacityCard(String city, List<com.propchk.be.dto.DailyAvailability> availabilityList) {
        List<Map<String, Object>> body = new ArrayList<>();
        body.add(Map.of(
                "type", "TextBlock",
                "size", "Large",
                "weight", "Bolder",
                "text", "⚡ Slot Capacity: " + city,
                "color", "Accent"
        ));
        body.add(Map.of(
                "type", "TextBlock",
                "text", "Here is the engineer slot availability for the next few days in **" + city + "**:",
                "wrap", true
        ));

        List<Map<String, String>> facts = new ArrayList<>();
        for (com.propchk.be.dto.DailyAvailability day : availabilityList) {
            String status = (day.isMorningAvailable() ? "Morning: Available" : "Morning: Full")
                    + " | "
                    + (day.isEveningAvailable() ? "Evening: Available" : "Evening: Full");
            facts.add(Map.of("title", day.getDate(), "value", status));
        }
        body.add(Map.of("type", "FactSet", "facts", facts));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", body);
        card.put("actions", List.of(Map.of(
                "type", "Action.OpenUrl",
                "title", "📅 Book Slot in Portal",
                "url", portalFrontendUrl.replaceAll("/$", "")
        )));

        return card;
    }

    /**
     * Feature 15: Help menu for Teams Bot.
     */
    public Map<String, Object> buildHelpMenuCard() {
        List<Map<String, Object>> body = new ArrayList<>();
        body.add(Map.of(
                "type", "TextBlock",
                "size", "Large",
                "weight", "Bolder",
                "text", "🤖 PropCheck Bot Command Menu",
                "color", "Accent"
        ));
        body.add(Map.of(
                "type", "TextBlock",
                "text", "You can message me with any of the following commands:",
                "wrap", true
        ));

        List<Map<String, String>> facts = List.of(
                Map.of("title", "lead <phone/id/name>", "value", "Fetch live customer inspection and payment details"),
                Map.of("title", "capacity <city>", "value", "Check upcoming available engineer capacity (e.g. `capacity Bangalore`)"),
                Map.of("title", "conflicts", "value", "Check if any bookings currently have leave conflicts"),
                Map.of("title", "help", "value", "Display this command list")
        );
        body.add(Map.of("type", "FactSet", "facts", facts));

        Map<String, Object> card = new LinkedHashMap<>();
        card.put("$schema", "http://adaptivecards.io/schemas/adaptive-card.json");
        card.put("type", "AdaptiveCard");
        card.put("version", "1.4");
        card.put("body", body);

        return card;
    }

    public void postToTeams(Map<String, Object> payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(new MediaType("application", "json", java.nio.charset.StandardCharsets.UTF_8));
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(payload, headers);
        restTemplate.postForEntity(teamsWebhookUrl, request, String.class);
    }
}
