package com.propchk.be.controller;

import com.propchk.be.dto.AssignEngineerRequest;
import com.propchk.be.dto.CreateBookingRequest;
import com.propchk.be.entity.Booking;
import com.propchk.be.entity.Engineer;
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

    public BookingController(BookingService bookingService,
                             BookingRepository bookingRepository,
                             EngineerRepository engineerRepository) {
        this.bookingService = bookingService;
        this.bookingRepository = bookingRepository;
        this.engineerRepository = engineerRepository;
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

    /** Cancel a booking */
    @PutMapping("/{id}/cancel")
    public ResponseEntity<Booking> cancelBooking(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.cancelBooking(id));
    }

    @PutMapping("/for-lead/{leadId}/cancel")
    public ResponseEntity<Booking> cancelBookingByLead(@PathVariable Long leadId) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.cancelBooking(active.getId()));
    }

    /** Mark inspection as completed */
    @PutMapping("/{id}/mark-done")
    public ResponseEntity<Booking> markInspectionDone(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.markInspectionDone(id));
    }

    @PutMapping("/for-lead/{leadId}/mark-done")
    public ResponseEntity<Booking> markInspectionDoneByLead(@PathVariable Long leadId) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.markInspectionDone(active.getId()));
    }

    /** Mark validation as completed */
    @PutMapping("/{id}/mark-validation-done")
    public ResponseEntity<Booking> markValidationDone(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.markValidationDone(id));
    }

    @PutMapping("/for-lead/{leadId}/mark-validation-done")
    public ResponseEntity<Booking> markValidationDoneByLead(@PathVariable Long leadId) {
        Booking active = getActiveBookingForLead(leadId);
        return ResponseEntity.ok(bookingService.markValidationDone(active.getId()));
    }

    /** Mark report as sent */
    @PutMapping("/{id}/report-sent")
    public ResponseEntity<Booking> markReportSent(@PathVariable Long id) {
        return ResponseEntity.ok(bookingService.markReportSent(id));
    }

    @PutMapping("/for-lead/{leadId}/report-sent")
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
}
