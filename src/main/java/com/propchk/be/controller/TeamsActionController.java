package com.propchk.be.controller;

import com.propchk.be.dto.AssignEngineerRequest;
import com.propchk.be.dto.DailyAvailability;
import com.propchk.be.entity.Booking;
import com.propchk.be.entity.Engineer;
import com.propchk.be.entity.Lead;
import com.propchk.be.repository.BookingRepository;
import com.propchk.be.repository.EngineerRepository;
import com.propchk.be.repository.LeadRepository;
import com.propchk.be.service.BookingService;
import com.propchk.be.service.TeamsNotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.view.RedirectView;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/teams")
public class TeamsActionController {

    private static final Logger logger = LoggerFactory.getLogger(TeamsActionController.class);

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final EngineerRepository engineerRepository;
    private final LeadRepository leadRepository;
    private final TeamsNotificationService teamsNotificationService;

    @Value("${portal.frontend.url:https://propchck.netlify.app}")
    private String portalFrontendUrl;

    public TeamsActionController(BookingService bookingService,
                                 BookingRepository bookingRepository,
                                 EngineerRepository engineerRepository,
                                 LeadRepository leadRepository,
                                 TeamsNotificationService teamsNotificationService) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.engineerRepository = engineerRepository;
        this.leadRepository = leadRepository;
        this.teamsNotificationService = teamsNotificationService;
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FEATURE 8: 1-Click Engineer Assignment (Two-Way Action from Teams)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * API action: Assigns an engineer to a booking.
     * POST /api/teams/actions/assign-engineer
     * Body: { "bookingId": 123, "engineerEmployeeNumber": "EMP-104", "assignedBy": "City Head" }
     */
    @PostMapping("/actions/assign-engineer")
    public ResponseEntity<Map<String, Object>> assignEngineerFromTeams(@RequestBody Map<String, Object> req) {
        Long bookingId = Long.valueOf(req.get("bookingId").toString());
        String empNum = req.get("engineerEmployeeNumber").toString();
        String assignedBy = req.getOrDefault("assignedBy", "City Head (Teams)").toString();

        AssignEngineerRequest assignReq = new AssignEngineerRequest();
        assignReq.setEngineerEmployeeNumber(empNum);
        assignReq.setCityHeadUserId(1L);

        Booking saved = bookingService.assignEngineer(bookingId, assignReq);
        Lead lead = leadRepository.findById(saved.getLeadId()).orElse(null);
        Engineer engineer = engineerRepository.findByEmployeeNumber(empNum).orElse(null);
        String engName = engineer != null ? engineer.getName() : empNum;

        // Post confirmation card back to Teams
        teamsNotificationService.sendEngineerAssignedConfirmationNotification(saved, lead, engName, assignedBy);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Engineer assigned successfully",
                "bookingId", saved.getId(),
                "engineer", engName
        ));
    }

    /**
     * Browser redirect action: Triggered when user clicks "Assign [Engineer]" button in Teams.
     * Performs assignment, sends confirmation to Teams, and redirects browser to customer portal.
     * GET /api/teams/actions/assign-engineer-redirect?bookingId=123&engineerEmp=EMP-104
     */
    @GetMapping("/actions/assign-engineer-redirect")
    public RedirectView assignEngineerRedirect(
            @RequestParam Long bookingId,
            @RequestParam String engineerEmp) {
        try {
            AssignEngineerRequest assignReq = new AssignEngineerRequest();
            assignReq.setEngineerEmployeeNumber(engineerEmp);
            assignReq.setCityHeadUserId(1L);

            Booking saved = bookingService.assignEngineer(bookingId, assignReq);
            Lead lead = leadRepository.findById(saved.getLeadId()).orElse(null);
            Engineer engineer = engineerRepository.findByEmployeeNumber(engineerEmp).orElse(null);
            String engName = engineer != null ? engineer.getName() : engineerEmp;

            teamsNotificationService.sendEngineerAssignedConfirmationNotification(saved, lead, engName, "City Head (1-Click Teams)");

            String targetUrl = portalFrontendUrl.replaceAll("/$", "");
            if (lead != null && lead.getId() != null) {
                targetUrl += "/leads/" + lead.getId();
            }
            return new RedirectView(targetUrl);
        } catch (Exception ex) {
            logger.error("Failed to assign engineer via Teams redirect: {}", ex.getMessage());
            return new RedirectView(portalFrontendUrl);
        }
    }

    /**
     * Demo helper: Fires a 1-click engineer assignment card into Teams.
     * POST /api/teams/actions/test-assign-card?leadId=6315
     */
    @PostMapping("/actions/test-assign-card")
    public ResponseEntity<Map<String, Object>> testAssignCard(@RequestParam(required = false) Long leadId) {
        Lead lead;
        if (leadId != null) {
            lead = leadRepository.findById(leadId).orElse(null);
        } else {
            lead = leadRepository.findAll().stream().findFirst().orElse(null);
        }

        if (lead == null) {
            lead = new Lead();
            lead.setId(6315L);
            lead.setCustomerName("Hari Krishna");
            lead.setProjectName("Prestige Falcon City");
            lead.setCity("Bangalore");
        }

        final Lead targetLead = lead;
        final Long targetLeadId = targetLead.getId();

        Booking booking = bookingRepository.findAll().stream()
                .filter(b -> Objects.equals(b.getLeadId(), targetLeadId))
                .findFirst().orElseGet(() -> {
                    Booking b = new Booking();
                    b.setId(101L);
                    b.setLeadId(targetLeadId);
                    b.setCity("Bangalore");
                    b.setDate(LocalDate.now().plusDays(2));
                    b.setSlot("MORNING");
                    b.setSlotTime("10:30");
                    b.setStatus("PENDING_ENGINEER_ASSIGNMENT");
                    return b;
                });

        List<Engineer> engineers = engineerRepository.findByMappedCityAndEmploymentStatusAndExitStatus(booking.getCity(), 0, 0);
        if (engineers.isEmpty()) {
            Engineer mockEng1 = new Engineer();
            mockEng1.setName("Arun Kumar");
            mockEng1.setEmployeeNumber("EMP-101");
            Engineer mockEng2 = new Engineer();
            mockEng2.setName("Suresh M");
            mockEng2.setEmployeeNumber("EMP-102");
            engineers = List.of(mockEng1, mockEng2);
        }

        teamsNotificationService.sendAssignEngineerCardNotification(booking, lead, engineers);
        return ResponseEntity.ok(Map.of("status", "ok", "message", "1-Click engineer assignment card sent to Teams!"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FEATURE 9: Mark "Validation Done" (Two-Way Action from Teams)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * API action: Marks booking validation done.
     * POST /api/teams/actions/validate-report
     * Body: { "bookingId": 123, "validatedBy": "City Head" }
     */
    @PostMapping("/actions/validate-report")
    public ResponseEntity<Map<String, Object>> validateReportFromTeams(@RequestBody Map<String, Object> req) {
        Long bookingId = Long.valueOf(req.get("bookingId").toString());
        String validatedBy = req.getOrDefault("validatedBy", "City Head (Teams)").toString();

        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        booking.setStatus("VALIDATION_DONE");
        Booking saved = bookingRepository.save(booking);

        Lead lead = leadRepository.findById(booking.getLeadId()).orElse(null);
        if (lead != null) {
            lead.setReportStatus("Validation Done");
            leadRepository.save(lead);
        }

        teamsNotificationService.sendValidationConfirmationNotification(saved, lead, validatedBy);

        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Report validated successfully",
                "bookingId", saved.getId()
        ));
    }

    /**
     * Browser redirect action: Triggered when user clicks "Validate & Release Report" button in Teams.
     * Performs validation, sends confirmation card to Teams, and redirects to customer portal.
     * GET /api/teams/actions/validate-report-redirect?bookingId=123
     */
    @GetMapping("/actions/validate-report-redirect")
    public RedirectView validateReportRedirect(@RequestParam Long bookingId) {
        try {
            Booking booking = bookingRepository.findById(bookingId).orElse(null);
            if (booking != null) {
                booking.setStatus("VALIDATION_DONE");
                Booking saved = bookingRepository.save(booking);

                Lead lead = leadRepository.findById(booking.getLeadId()).orElse(null);
                if (lead != null) {
                    lead.setReportStatus("Validation Done");
                    leadRepository.save(lead);
                }

                teamsNotificationService.sendValidationConfirmationNotification(saved, lead, "City Head (1-Click Teams)");

                String targetUrl = portalFrontendUrl.replaceAll("/$", "");
                if (lead != null && lead.getId() != null) {
                    targetUrl += "/leads/" + lead.getId();
                }
                return new RedirectView(targetUrl);
            }
            return new RedirectView(portalFrontendUrl);
        } catch (Exception ex) {
            logger.error("Failed to validate report via Teams redirect: {}", ex.getMessage());
            return new RedirectView(portalFrontendUrl);
        }
    }

    /**
     * Demo helper: Fires a validation request card into Teams.
     * POST /api/teams/actions/test-validation-card?leadId=6315
     */
    @PostMapping("/actions/test-validation-card")
    public ResponseEntity<Map<String, Object>> testValidationCard(@RequestParam(required = false) Long leadId) {
        Lead lead;
        if (leadId != null) {
            lead = leadRepository.findById(leadId).orElse(null);
        } else {
            lead = leadRepository.findAll().stream().findFirst().orElse(null);
        }

        if (lead == null) {
            lead = new Lead();
            lead.setId(6315L);
            lead.setCustomerName("Hari Krishna");
            lead.setProjectName("Prestige Falcon City");
            lead.setCity("Bangalore");
        }

        Booking booking = new Booking();
        booking.setId(102L);
        booking.setLeadId(lead.getId());
        booking.setCity("Bangalore");
        booking.setDate(LocalDate.now());
        booking.setStatus("INSPECTION_DONE");

        teamsNotificationService.sendValidationRequestNotification(booking, lead, "Inspection completed on-site. 14 snags logged. Master bedroom seepage verified.");
        return ResponseEntity.ok(Map.of("status", "ok", "message", "Validation request card sent to Teams!"));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FEATURE 14: Search & Share Zoho Lead (Message Extension API)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Searches leads by name, phone, email, or project and returns Adaptive Cards.
     * Used by Teams Message Extensions and internal search.
     * GET /api/teams/search?query=Hari
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchLeadsForTeams(@RequestParam String query) {
        if (query == null || query.trim().isEmpty()) {
            return ResponseEntity.ok(Map.of("results", Collections.emptyList()));
        }

        String q = query.trim().toLowerCase();
        List<Lead> matches = leadRepository.findAll().stream()
                .filter(l -> (l.getCustomerName() != null && l.getCustomerName().toLowerCase().contains(q))
                        || (l.getPhone() != null && l.getPhone().contains(q))
                        || (l.getEmail() != null && l.getEmail().toLowerCase().contains(q))
                        || (l.getProjectName() != null && l.getProjectName().toLowerCase().contains(q))
                        || (l.getId() != null && l.getId().toString().equals(q)))
                .limit(5)
                .collect(Collectors.toList());

        List<Map<String, Object>> cards = new ArrayList<>();
        for (Lead lead : matches) {
            Booking active = bookingRepository.findActiveBookingsByLeadIds(List.of(lead.getId())).stream().findFirst().orElse(null);
            cards.add(teamsNotificationService.buildLeadDetailCard(lead, active));
        }

        return ResponseEntity.ok(Map.of(
                "query", query,
                "count", matches.size(),
                "cards", cards
        ));
    }

    // ═════════════════════════════════════════════════════════════════════════
    // FEATURE 15: Conversational Bot Query API (@PropCheckBot)
    // ═════════════════════════════════════════════════════════════════════════

    /**
     * Handles natural language queries sent to @PropCheck in Teams.
     * Supports:
     * - "lead <name/phone/id>"
     * - "capacity <city>"
     * - "conflicts"
     * - "help"
     * POST /api/teams/bot
     * Body: { "text": "lead Hari Krishna" }
     */
    @PostMapping("/bot")
    public ResponseEntity<Map<String, Object>> handleBotQuery(@RequestBody Map<String, Object> body) {
        String text = body.getOrDefault("text", "help").toString().trim();
        String lower = text.toLowerCase();

        // 1. Help command
        if (lower.equals("help") || lower.equals("hi") || lower.equals("hello")) {
            return ResponseEntity.ok(Map.of(
                    "type", "AdaptiveCard",
                    "card", teamsNotificationService.buildHelpMenuCard()
            ));
        }

        // 2. Capacity command: e.g. "capacity Bangalore"
        if (lower.startsWith("capacity")) {
            String city = "Bangalore";
            String[] parts = text.split("\\s+");
            if (parts.length > 1) {
                city = parts[1];
            }
            List<DailyAvailability> availability = bookingService.getAvailabilityCalendar(city, LocalDate.now(), 4, "INSPECTION");
            return ResponseEntity.ok(Map.of(
                    "type", "AdaptiveCard",
                    "card", teamsNotificationService.buildCapacityCard(city, availability)
            ));
        }

        // 3. Conflicts command: e.g. "conflicts"
        if (lower.contains("conflict")) {
            List<Booking> conflicts = bookingRepository.findAll().stream()
                    .filter(b -> "CONFLICT".equals(b.getStatus()))
                    .collect(Collectors.toList());

            List<Map<String, String>> facts = new ArrayList<>();
            for (Booking c : conflicts) {
                Lead l = leadRepository.findById(c.getLeadId()).orElse(null);
                String cust = l != null ? l.getCustomerName() : "Lead #" + c.getLeadId();
                facts.add(Map.of(
                        "title", c.getDate() + " (" + c.getCity() + ")",
                        "value", cust + " • Eng: " + c.getEngineerEmployeeNumber() + " - " + c.getConflictReason()
                ));
            }

            Map<String, Object> card = new LinkedHashMap<>();
            card.put("type", "AdaptiveCard");
            card.put("version", "1.4");
            card.put("body", List.of(
                    Map.of("type", "TextBlock", "size", "Large", "weight", "Bolder", "text", "🚨 Active Conflicts: " + conflicts.size(), "color", conflicts.isEmpty() ? "Good" : "Attention"),
                    Map.of("type", "FactSet", "facts", facts.isEmpty() ? List.of(Map.of("title", "Status", "value", "No active conflicts detected!")) : facts)
            ));

            return ResponseEntity.ok(Map.of("type", "AdaptiveCard", "card", card));
        }

        // 4. Lead search: e.g. "lead 6315" or "lead Hari" or just "Hari"
        String query = text;
        if (lower.startsWith("lead ")) {
            query = text.substring(5).trim();
        }

        String q = query.toLowerCase();
        Lead lead = leadRepository.findAll().stream()
                .filter(l -> (l.getCustomerName() != null && l.getCustomerName().toLowerCase().contains(q))
                        || (l.getPhone() != null && l.getPhone().contains(q))
                        || (l.getId() != null && l.getId().toString().equals(q)))
                .findFirst().orElse(null);

        if (lead != null) {
            Booking active = bookingRepository.findActiveBookingsByLeadIds(List.of(lead.getId())).stream().findFirst().orElse(null);
            return ResponseEntity.ok(Map.of(
                    "type", "AdaptiveCard",
                    "card", teamsNotificationService.buildLeadDetailCard(lead, active)
            ));
        }

        // Not found fallback
        return ResponseEntity.ok(Map.of(
                "type", "message",
                "text", "❌ No lead found matching: '" + query + "'. Type `help` to see available commands."
        ));
    }
}
