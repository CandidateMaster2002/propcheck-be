package com.propchk.be.controller;

import com.propchk.be.dto.AssignEngineerRequest;
import com.propchk.be.dto.CreateBookingRequest;
import com.propchk.be.entity.Booking;
import com.propchk.be.entity.Engineer;
import com.propchk.be.entity.Lead;
import com.propchk.be.repository.BookingRepository;
import com.propchk.be.repository.EngineerRepository;
import com.propchk.be.service.BookingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final BookingRepository bookingRepository;
    private final EngineerRepository engineerRepository;
    private final com.propchk.be.service.TeamsNotificationService teamsNotificationService;
    private final com.propchk.be.repository.LeadRepository leadRepository;

    public BookingController(BookingService bookingService,
                             BookingRepository bookingRepository,
                             EngineerRepository engineerRepository,
                             com.propchk.be.service.TeamsNotificationService teamsNotificationService,
                             com.propchk.be.repository.LeadRepository leadRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.engineerRepository = engineerRepository;
        this.teamsNotificationService = teamsNotificationService;
        this.leadRepository = leadRepository;
    }

    /**
     * Check available capacity before creating a booking.
     * Returns: { availableSlots: N, canBook: true/false }
     */
    @GetMapping("/availability")
    public ResponseEntity<Map<String, Object>> checkAvailability(
            @RequestParam String city,
            @RequestParam String date,
            @RequestParam String slot,
            @RequestParam String bookingType) {
        int capacity = bookingService.getAvailableCapacity(
                city, LocalDate.parse(date), slot, bookingType);
        return ResponseEntity.ok(Map.of(
                "city", city,
                "date", date,
                "slot", slot,
                "availableSlots", capacity,
                "canBook", capacity > 0
        ));
    }

    /**
     * Bulk check availability for the frontend date picker UI.
     * Returns a summary of morning/evening availability for the next N days.
     */
    @GetMapping("/availability-calendar")
    public ResponseEntity<List<com.propchk.be.dto.DailyAvailability>> getAvailabilityCalendar(
            @RequestParam(required = false, defaultValue = "") String city,
            @RequestParam(required = false) String startDate,
            @RequestParam(defaultValue = "15") int days,
            @RequestParam(required = false, defaultValue = "INSPECTION") String bookingType) {
        
        LocalDate parsedDate;
        try {
            parsedDate = (startDate != null && !startDate.trim().isEmpty() && !startDate.equals("undefined")) 
                ? LocalDate.parse(startDate) : LocalDate.now();
        } catch (Exception e) {
            parsedDate = LocalDate.now();
        }

        return ResponseEntity.ok(bookingService.getAvailabilityCalendar(
                city, parsedDate, days, bookingType));
    }

    /** Create a new booking */
    @PostMapping
    public ResponseEntity<Booking> createBooking(@RequestBody CreateBookingRequest request) {
        Booking booking = bookingService.createBooking(request);
        return ResponseEntity.ok(booking);
    }

    /** Sales person approves a PENDING_APPROVAL booking */
    @PutMapping("/{id}/approve")
    public ResponseEntity<Booking> approveBooking(
            @PathVariable Long id,
            @RequestParam Long salesUserId) {
        return ResponseEntity.ok(bookingService.approveBooking(id, salesUserId));
    }

    /** City Head assigns an engineer to a booking */
    @PutMapping("/{id}/assign-engineer")
    public ResponseEntity<Booking> assignEngineer(
            @PathVariable Long id,
            @RequestBody AssignEngineerRequest request) {
        return ResponseEntity.ok(bookingService.assignEngineer(id, request));
    }

    @PutMapping("/for-lead/{leadId}/assign-engineer")
    public ResponseEntity<Booking> assignEngineerByLead(@PathVariable Long leadId, @RequestBody AssignEngineerRequest request) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.assignEngineer(active.getId(), request));
    }

    /** Cancel a booking with reason */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Booking> cancelBooking(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Cancelled via portal") String reason,
            @RequestParam(required = false, defaultValue = "Operations / Portal") String cancelledBy) {
        return ResponseEntity.ok(bookingService.cancelBooking(id, reason, cancelledBy));
    }

    @PutMapping("/for-lead/{leadId}/cancel")
    public ResponseEntity<Booking> cancelBookingByLead(
            @PathVariable Long leadId,
            @RequestParam(required = false, defaultValue = "Cancelled via portal") String reason,
            @RequestParam(required = false, defaultValue = "Operations / Portal") String cancelledBy) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.cancelBooking(active.getId(), reason, cancelledBy));
    }

    /** Postpone a booking with remark */
    @PutMapping("/{id}/postpone")
    public ResponseEntity<Booking> postponeBooking(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Postponed via portal") String remark,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        return ResponseEntity.ok(bookingService.postponeBooking(id, remark, userId));
    }

    @PutMapping("/for-lead/{leadId}/postpone")
    public ResponseEntity<Booking> postponeBookingByLead(
            @PathVariable Long leadId,
            @RequestParam(required = false, defaultValue = "Postponed via portal") String remark,
            @RequestParam(required = false, defaultValue = "1") Long userId) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.postponeBooking(active.getId(), remark, userId));
    }

    /** Mark inspection as completed */
    /** Mark inspection as completed */
    @PutMapping("/{id}/mark-done")
    public ResponseEntity<Booking> markInspectionDone(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> payload) {
        String reportUrl = payload != null && payload.get("reportUrl") != null ? payload.get("reportUrl").toString() : null;
        Integer insScore = null;
        if (payload != null && payload.get("insScore") != null) {
            try { insScore = Integer.parseInt(payload.get("insScore").toString()); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(bookingService.markInspectionDone(id, reportUrl, insScore));
    }

    @PutMapping("/for-lead/{leadId}/mark-done")
    public ResponseEntity<Booking> markInspectionDoneByLead(
            @PathVariable Long leadId,
            @RequestBody(required = false) java.util.Map<String, Object> payload) {
        Booking active = getActiveBookingForLead(leadId);
        String reportUrl = payload != null && payload.get("reportUrl") != null ? payload.get("reportUrl").toString() : null;
        Integer insScore = null;
        if (payload != null && payload.get("insScore") != null) {
            try { insScore = Integer.parseInt(payload.get("insScore").toString()); } catch (Exception ignored) {}
        }
        return ResponseEntity.ok(bookingService.markInspectionDone(active.getId(), reportUrl, insScore));
    }

    /** Mark validation as completed */
    @PutMapping("/{id}/mark-validation-done")
    public ResponseEntity<Booking> markValidationDone(
            @PathVariable Long id,
            @RequestBody(required = false) java.util.Map<String, Object> payload) {
        String remarks = payload != null && payload.get("remarks") != null ? payload.get("remarks").toString() : null;
        return ResponseEntity.ok(bookingService.markValidationDone(id, remarks));
    }

    @PutMapping("/for-lead/{leadId}/mark-validation-done")
    public ResponseEntity<Booking> markValidationDoneByLead(
            @PathVariable Long leadId,
            @RequestBody(required = false) java.util.Map<String, Object> payload) {
        Booking active = getActiveBookingForLead(leadId);
        String remarks = payload != null && payload.get("remarks") != null ? payload.get("remarks").toString() : null;
        return ResponseEntity.ok(bookingService.markValidationDone(active.getId(), remarks));
    }

    /** Webhook for External Admin Portal to report inspection completion directly */
    @PostMapping("/webhooks/completed")
    public ResponseEntity<java.util.Map<String, Object>> inspectionCompletedWebhook(
            @RequestBody java.util.Map<String, Object> payload) {
        Long leadId = null;
        Long bookingId = null;
        if (payload.get("leadId") != null) {
            try { leadId = Long.parseLong(payload.get("leadId").toString()); } catch (Exception ignored) {}
        }
        if (payload.get("bookingId") != null) {
            try { bookingId = Long.parseLong(payload.get("bookingId").toString()); } catch (Exception ignored) {}
        }

        // Fallback matching by customer phone or email if leadId/bookingId not directly supplied
        if (bookingId == null && leadId == null) {
            String phone = payload.get("ownerPhone") != null ? payload.get("ownerPhone").toString() : (payload.get("phone") != null ? payload.get("phone").toString() : null);
            String email = payload.get("ownerEmail") != null ? payload.get("ownerEmail").toString() : (payload.get("email") != null ? payload.get("email").toString() : null);
            if (phone != null && !phone.trim().isEmpty()) {
                String cleanDigits = phone.replaceAll("[^0-9]", "");
                if (cleanDigits.length() >= 10) {
                    cleanDigits = cleanDigits.substring(cleanDigits.length() - 10);
                }
                var leads = leadRepository.findByPhoneEndingWith(cleanDigits);
                if (!leads.isEmpty()) {
                    leadId = leads.get(0).getId();
                }
            }
            if (leadId == null && email != null && !email.trim().isEmpty()) {
                leadRepository.findFirstByEmailIgnoreCase(email.trim()).ifPresent(l -> {
                    // assigned via lambda below
                });
                var leadOpt = leadRepository.findFirstByEmailIgnoreCase(email.trim());
                if (leadOpt.isPresent()) {
                    leadId = leadOpt.get().getId();
                }
            }
        }

        String reportUrl = payload.get("reportUrl") != null ? payload.get("reportUrl").toString() : null;
        Integer insScore = null;
        if (payload.get("insScore") != null) {
            try { insScore = Integer.parseInt(payload.get("insScore").toString()); } catch (Exception ignored) {}
        }

        Booking booking;
        if (bookingId != null) {
            booking = bookingService.markInspectionDone(bookingId, reportUrl, insScore);
        } else if (leadId != null) {
            Booking active = getActiveBookingForLead(leadId);
            booking = bookingService.markInspectionDone(active.getId(), reportUrl, insScore);
        } else {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", "Could not resolve booking: leadId, bookingId, phone, or email must be provided in webhook payload"));
        }

        return ResponseEntity.ok(java.util.Map.of(
            "status", "SUCCESS",
            "message", "Inspection successfully marked as done in PropCheck",
            "bookingId", booking.getId(),
            "bookingStatus", booking.getStatus()
        ));
    }

    /** VALIDATOR: Approve a completed inspection report */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Booking> approveReport(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.approveReport(id));
    }

    /** VALIDATOR: Reject a completed inspection report with remarks */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Booking> rejectReport(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "Rejected by validator") String remarks) {
        return ResponseEntity.ok(bookingService.rejectReport(id, remarks));
    }

    /** Mark report as sent */
    @PutMapping(value = {"/{id}/report-sent", "/{id}/send-report"})
    public ResponseEntity<Booking> markReportSent(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.markReportSent(id));
    }

    @PutMapping(value = {"/for-lead/{leadId}/report-sent", "/for-lead/{leadId}/send-report"})
    public ResponseEntity<Booking> markReportSentByLead(@PathVariable Long leadId) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.markReportSent(active.getId()));
    }

    private Booking getActiveBookingForLead(Long leadId) {
        List<Booking> activeBookings = bookingRepository.findActiveBookingsByLeadIds(java.util.List.of(leadId));
        if (activeBookings.isEmpty()) {
            throw new RuntimeException("No active booking found for lead: " + leadId);
        }
        return activeBookings.get(0);
    }

    /** Reschedule a booking — same rules as creation apply (capacity, payment, city) */
    @PutMapping("/{id}/reschedule")
    public ResponseEntity<Booking> rescheduleBooking(
            @PathVariable Long id,
            @RequestBody com.propchk.be.dto.RescheduleBookingRequest request) {
        return ResponseEntity.ok(bookingService.rescheduleBooking(id, request));
    }

    @PutMapping("/for-lead/{leadId}/reschedule")
    public ResponseEntity<Booking> rescheduleBookingByLead(
            @PathVariable Long leadId,
            @RequestBody com.propchk.be.dto.RescheduleBookingRequest request) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.rescheduleBooking(active.getId(), request));
    }

    /** Get a single booking */
    @GetMapping("/{id}")
    public ResponseEntity<Booking> getBooking(@PathVariable Long id) {
        return bookingRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** List all bookings (optionally filter by city or status) */
    @GetMapping
    public ResponseEntity<List<Booking>> listBookings(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String status) {
        List<Booking> bookings;
        if (city != null && status != null) {
            bookings = bookingRepository.findByCityAndStatus(city, status);
        } else if (city != null) {
            bookings = bookingRepository.findByCity(city);
        } else if (status != null) {
            bookings = bookingRepository.findByStatus(status);
        } else {
            bookings = bookingRepository.findAll();
        }
        return ResponseEntity.ok(bookings);
    }

    /** All CONFLICT bookings for a city (City Head dashboard) */
    @GetMapping("/conflicts")
    public ResponseEntity<List<Booking>> getConflicts(
            @RequestParam(required = false) String city) {
        List<Booking> conflicts = city != null
                ? bookingRepository.findByCityAndStatusOrderByDateAsc(city, "CONFLICT")
                : bookingRepository.findByStatus("CONFLICT");
        return ResponseEntity.ok(conflicts);
    }

    /** Customer's own bookings */
    @GetMapping("/my")
    public ResponseEntity<List<Booking>> getMyBookings(@RequestParam Long userId) {
        return ResponseEntity.ok(bookingRepository.findByCreatedByUserIdOrderByDateDesc(userId));
    }

    /** Safely parse dates, or return bad request */
    @GetMapping("/available-engineers")
    public ResponseEntity<List<Engineer>> getAvailableEngineers(
            @RequestParam String city,
            @RequestParam String date,
            @RequestParam String slot) {
        LocalDate bookingDate;
        try {
            // safely handle strings like "2026-09-28 10:00" if FE sends it directly
            bookingDate = LocalDate.parse(date.split(" ")[0]); 
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
        
        // Get all active engineers in city
        List<Engineer> all = engineerRepository
                .findByMappedCityAndEmploymentStatusAndExitStatus(city, 0, 0);
        // Filter out those on leave or already booked in this slot
        List<Engineer> available = all.stream()
                .filter(e -> {
                    boolean onLeave = !bookingService.getEngineerLeaveRepository()
                            .findApprovedLeavesOnDate(e.getEmployeeNumber(), bookingDate).isEmpty();
                    if (onLeave) return false;
                    // Check if already booked in this slot
                    long alreadyBooked = bookingRepository.findAll().stream()
                            .filter(b -> e.getEmployeeNumber().equals(b.getEngineerEmployeeNumber())
                                    && bookingDate.equals(b.getDate())
                                    && slot.equals(b.getSlot())
                                    && !"CANCELLED".equals(b.getStatus()))
                            .count();
                    return alreadyBooked == 0;
                })
                .toList();
        return ResponseEntity.ok(available);
    }

    /** Easier endpoint for FE: just pass the booking ID! */
    @GetMapping("/{bookingId}/available-engineers")
    public ResponseEntity<List<Engineer>> getAvailableEngineersByBooking(@PathVariable Long bookingId) {
        return bookingRepository.findById(bookingId).map(booking -> 
            getAvailableEngineers(booking.getCity(), booking.getDate().toString(), booking.getSlot())
        ).orElse(ResponseEntity.notFound().build());
    }

    /** EVEN EASIER endpoint for FE: just pass the LEAD ID (since the button is on the Lead row) */
    @GetMapping("/for-lead/{leadId}/available-engineers")
    public ResponseEntity<List<Engineer>> getAvailableEngineersByLead(@PathVariable Long leadId) {
        // Find the active booking for this lead
        List<Booking> activeBookings = bookingRepository.findActiveBookingsByLeadIds(java.util.List.of(leadId));
        if (activeBookings.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Booking booking = activeBookings.get(0);
        return getAvailableEngineers(booking.getCity(), booking.getDate().toString(), booking.getSlot());
    }

    /**
     * Demo endpoint: Triggers an urgent conflict alert card to Microsoft Teams for a booking.
     * POST /api/bookings/test-conflict-teams?bookingId=1
     */
    @PostMapping("/test-conflict-teams")
    public ResponseEntity<Map<String, Object>> testConflictAlert(@RequestParam(required = false) Long bookingId) {
        Booking booking;
        if (bookingId != null) {
            booking = bookingRepository.findById(bookingId).orElse(null);
        } else {
            booking = bookingRepository.findAll().stream().findFirst().orElse(null);
        }

        if (booking == null) {
            // Create a mock booking for demo if database has none
            booking = new Booking();
            booking.setLeadId(6315L);
            booking.setCity("Bangalore");
            booking.setDate(LocalDate.now().plusDays(1));
            booking.setSlot("MORNING");
            booking.setSlotTime("10:30");
            booking.setEngineerEmployeeNumber("EMP-104");
        }

        final Booking targetBooking = booking;
        final Long leadId = targetBooking.getLeadId();

        Lead lead = leadRepository.findById(leadId).orElseGet(() -> {
            Lead dummy = new Lead();
            dummy.setId(leadId);
            dummy.setCustomerName("Hari Krishna");
            dummy.setProjectName("Prestige Falcon City");
            dummy.setCity("Bangalore");
            return dummy;
        });

        String engName = "Rahul Sharma";
        String leaveType = "Sick / Medical Leave";
        String reason = "Engineer EMP-104 has emergency approved leave on " + booking.getDate() + " (" + leaveType + ")";

        teamsNotificationService.sendConflictAlertNotification(booking, lead, engName, leaveType, reason);
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "Conflict alert sent to Microsoft Teams!",
                "customer", lead.getCustomerName(),
                "engineer", engName
        ));
    }
}
