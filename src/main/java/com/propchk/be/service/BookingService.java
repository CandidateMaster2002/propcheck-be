package com.propchk.be.service;

import com.propchk.be.dto.AssignEngineerRequest;
import com.propchk.be.dto.CreateBookingRequest;
import com.propchk.be.dto.RescheduleBookingRequest;
import com.propchk.be.entity.Booking;
import com.propchk.be.entity.Engineer;
import com.propchk.be.entity.Lead;
import com.propchk.be.entity.User;
import com.propchk.be.exception.InvalidCredentialsException;
import com.propchk.be.repository.BookingRepository;
import com.propchk.be.repository.EngineerLeaveRepository;
import com.propchk.be.repository.EngineerRepository;
import com.propchk.be.repository.LeadRepository;
import com.propchk.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Service
public class BookingService {

    private static final Logger logger = LoggerFactory.getLogger(BookingService.class);

    private static final Set<String> VALID_MORNING_TIMES = Set.of("10:00", "10:30", "11:00", "11:30");
    private static final Set<String> VALID_EVENING_TIMES = Set.of("14:00", "14:30", "15:00", "15:30", "16:00");

    /**
     * Cities where PropCheck has resident engineers.
     * Any city outside this set requires manual sales approval regardless of payment or role.
     */
    private static final Set<String> STANDARD_CITIES = Set.of(
            "Bangalore", "Hyderabad", "Delhi NCR", "Pune", "Mumbai"
    );

    /**
     * Inspection plan types that allow the customer to also book a Re-inspection.
     * Sourced from the Zoho CRM `param` / inspectionType field on the Lead.
     */
    private static final Set<String> PLANS_WITH_REINSPECTION = Set.of(
            "Inspection+Re-Inspection",
            "Inspection+Re-Inspection+Interior"
    );

    private final BookingRepository bookingRepository;
    private final EngineerRepository engineerRepository;
    private final EngineerLeaveRepository leaveRepository;
    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    public BookingService(BookingRepository bookingRepository,
                          EngineerRepository engineerRepository,
                          EngineerLeaveRepository leaveRepository,
                          LeadRepository leadRepository,
                          UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.engineerRepository = engineerRepository;
        this.leaveRepository = leaveRepository;
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
    }

    /**
     * Returns how many free engineer slots are available for a given city + date + slot.
     * Formula: engineers without approved leave - existing active bookings for same slot.
     */
    public int getAvailableCapacity(String city, LocalDate date, String slot, String bookingType) {
        // 1. All active QC engineers in the city
        List<Engineer> engineers = engineerRepository
                .findByMappedCityAndEmploymentStatusAndExitStatus(city, 0, 0);

        // 2. Remove engineers on approved leave that day
        long available = engineers.stream()
                .filter(e -> leaveRepository.findApprovedLeavesOnDate(e.getEmployeeNumber(), date).isEmpty())
                .count();

        // 3. Subtract already-booked slots
        long alreadyBooked = bookingRepository.countActiveBookings(city, date, slot);

        int capacity = (int) (available - alreadyBooked);
        logger.info("Availability check — city={} date={} slot={} | engineers={} available={} booked={} capacity={}",
                city, date, slot, engineers.size(), available, alreadyBooked, Math.max(capacity, 0));
        return Math.max(capacity, 0);
    }
    
    /**
     * Bulk checks availability for a range of dates.
     * Used by the frontend date-picker UI to disable unavailable buttons upfront.
     * Rewritten to fetch all data in 3 queries instead of N*N queries for extreme speed.
     */
    public List<com.propchk.be.dto.DailyAvailability> getAvailabilityCalendar(
            String city, LocalDate startDate, int days, String bookingType) {
        
        List<com.propchk.be.dto.DailyAvailability> result = new java.util.ArrayList<>();
        boolean isStandardCity = STANDARD_CITIES.contains(city);
        LocalDate endDate = startDate.plusDays(days - 1);

        if (!isStandardCity) {
            // Non-standard cities are manually reviewed, so they appear "available" to book
            for (int i = 0; i < days; i++) {
                LocalDate date = startDate.plusDays(i);
                com.propchk.be.dto.DailyAvailability day = new com.propchk.be.dto.DailyAvailability();
                day.setDate(date.toString());
                day.setMorningAvailable(true);
                day.setEveningAvailable(true);
                day.setAnyAvailable(true);
                result.add(day);
            }
            return result;
        }

        // --- OPTIMIZED BULK FETCH ---
        List<Engineer> engineers = engineerRepository.findByMappedCityAndEmploymentStatusAndExitStatus(city, 0, 0);
        List<String> empNums = engineers.stream().map(Engineer::getEmployeeNumber).collect(java.util.stream.Collectors.toList());
        
        List<com.propchk.be.entity.EngineerLeave> leaves = empNums.isEmpty() ? java.util.Collections.emptyList() : 
            getEngineerLeaveRepository().findApprovedLeavesInRange(empNums, startDate, endDate);
        
        List<Booking> bookings = bookingRepository.findActiveBookingsByCityAndDateRange(city, startDate, endDate);

        // Pre-compute leaves per date (Note: multiple engineers can be on leave the same day)
        java.util.Map<LocalDate, java.util.Set<String>> engineersOnLeavePerDate = new java.util.HashMap<>();
        for (com.propchk.be.entity.EngineerLeave leave : leaves) {
            LocalDate cur = leave.getFromDate();
            LocalDate endL = leave.getToDate();
            while (!cur.isAfter(endL)) {
                if (!cur.isBefore(startDate) && !cur.isAfter(endDate)) {
                    engineersOnLeavePerDate.computeIfAbsent(cur, k -> new java.util.HashSet<>()).add(leave.getEmployeeNumber());
                }
                cur = cur.plusDays(1);
            }
        }

        // Pre-compute bookings per date -> slot -> count
        java.util.Map<LocalDate, java.util.Map<String, Long>> bookingsPerDateSlot = new java.util.HashMap<>();
        for (Booking b : bookings) {
            bookingsPerDateSlot.computeIfAbsent(b.getDate(), k -> new java.util.HashMap<>())
                .merge(b.getSlot(), 1L, Long::sum);
        }

        for (int i = 0; i < days; i++) {
            LocalDate date = startDate.plusDays(i);
            com.propchk.be.dto.DailyAvailability day = new com.propchk.be.dto.DailyAvailability();
            day.setDate(date.toString());

            int onLeave = engineersOnLeavePerDate.getOrDefault(date, java.util.Collections.emptySet()).size();
            int baseAvailable = Math.max(0, engineers.size() - onLeave);

            long morningBooked = bookingsPerDateSlot.getOrDefault(date, java.util.Collections.emptyMap()).getOrDefault("MORNING", 0L);
            long eveningBooked = bookingsPerDateSlot.getOrDefault(date, java.util.Collections.emptyMap()).getOrDefault("EVENING", 0L);

            int morningCapacity = (int) (baseAvailable - morningBooked);
            int eveningCapacity = (int) (baseAvailable - eveningBooked);

            day.setMorningAvailable(morningCapacity > 0);
            day.setEveningAvailable(eveningCapacity > 0);
            day.setAnyAvailable(morningCapacity > 0 || eveningCapacity > 0);
            
            result.add(day);
        }
        return result;
    }


    /**
     * Creates a booking after validating:
     * 1. Lead exists
     * 2. Booking type eligibility (re-inspection rules)
     * 3. Capacity availability
     * 4. Slot time validity
     * Sets status based on payment status and who is booking.
     */
    public Booking createBooking(CreateBookingRequest req) {
        Lead lead = leadRepository.findById(req.getLeadId())
                .orElseThrow(() -> new RuntimeException("Lead not found: " + req.getLeadId()));

        User creator = userRepository.findById(req.getCreatedByUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + req.getCreatedByUserId()));

        String role = creator.getRole().name();
        String paymentStatus = lead.getPaymentStatus();

        // PAYMENT STATUS GUARDS (applies to ALL roles)
        // 1. Refunded leads cannot be booked by anyone
        if ("Refunded".equals(paymentStatus)) {
            throw new RuntimeException(
                "Booking not allowed: this lead has been refunded. Please contact the sales team.");
        }

        // 2. Unpaid leads can only be booked by Sales/City Head/Admin — not by customer
        if ("CUSTOMER".equals(role) && "Unpaid".equals(paymentStatus)) {
            throw new RuntimeException(
                "Booking not allowed: your payment is pending. Please complete the payment to proceed.");
        }

        // DUPLICATE ACTIVE BOOKING GUARD (applies to ALL roles)
        if ("INSPECTION".equals(req.getBookingType())) {
            boolean alreadyHasActive = bookingRepository.existsActiveBookingForLeadAndType(lead.getId(), "INSPECTION");
            if (alreadyHasActive) {
                throw new RuntimeException(
                    "This lead already has an active inspection booking. " +
                    "Please cancel or reschedule the existing booking first.");
            }
        }

        // Validate slot time
        validateSlotTime(req.getSlot(), req.getSlotTime());

        LocalDate bookingDate = LocalDate.parse(req.getDate());
        if (bookingDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("Cannot book a date in the past.");
        }

        // Validate booking type eligibility
        validateBookingType(lead, req.getBookingType());

        boolean isStandardCity = STANDARD_CITIES.contains(req.getCity());

        // Capacity check — only for standard cities; other cities are manually approved by sales
        if (isStandardCity) {
            int capacity = getAvailableCapacity(req.getCity(), bookingDate, req.getSlot(), req.getBookingType());
            if (capacity <= 0) {
                throw new RuntimeException(
                    "No engineers available in " + req.getCity() + " on " + req.getDate()
                    + " for the " + req.getSlot() + " slot. Please choose a different date or time.");
            }
        }

        // Determine initial status
        String status;

        if (!"CUSTOMER".equals(role)) {
            // Sales/City Head/Admin — always goes to pending assignment regardless of payment
            // (Refunded was already blocked above)
            status = "PENDING_ENGINEER_ASSIGNMENT";
        } else {
            // Customer self-serve bookings
            if (!isStandardCity) {
                // Non-standard city: always needs sales approval
                status = "PENDING_APPROVAL";
                logger.info("Booking for non-standard city '{}' by customer — routing to PENDING_APPROVAL.", req.getCity());
            } else {
                // Standard city customer: payment determines routing
                if ("Fully Paid".equals(paymentStatus)) {
                    status = "PENDING_ENGINEER_ASSIGNMENT"; // Auto-approved
                } else {
                    // Partially Paid → needs approval (Unpaid already blocked above)
                    status = "PENDING_APPROVAL";
                }
            }
        }

        Booking booking = new Booking();
        booking.setLeadId(req.getLeadId());
        booking.setBookingType(req.getBookingType());
        booking.setCity(req.getCity());
        booking.setDate(bookingDate);
        booking.setSlot(req.getSlot());
        booking.setSlotTime(req.getSlotTime());
        booking.setStatus(status);
        booking.setCreatedByUserId(req.getCreatedByUserId());
        booking.setCreatedAt(Instant.now());
        booking.setUpdatedAt(Instant.now());

        Booking saved = bookingRepository.save(booking);

        // Update Lead fields to reflect the new booking
        lead.setInspectionDateAndTime(req.getDate() + " " + req.getSlotTime());
        lead.setBookedBy(creator.getName());
        leadRepository.save(lead);

        logger.info("Booking created — ID {} | Lead {} | {} | {} {} | {} | Status: {}",
                saved.getId(), lead.getCustomerName(), req.getCity(),
                req.getDate(), req.getSlot(), req.getBookingType(), status);
        return saved;
    }

    /**
     * Sales person approves a PENDING_APPROVAL booking (for partial-pay customers).
     */
    public Booking approveBooking(Long bookingId, Long salesUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (!"PENDING_APPROVAL".equals(booking.getStatus())) {
            throw new RuntimeException("Booking is not in PENDING_APPROVAL status.");
        }

        booking.setStatus("PENDING_ENGINEER_ASSIGNMENT");
        booking.setApprovedByUserId(salesUserId);
        booking.setUpdatedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);
        logger.info("Booking {} approved by User {}", bookingId, salesUserId);
        return saved;
    }

    /**
     * City Head assigns a specific engineer to a PENDING_ENGINEER_ASSIGNMENT or CONFLICT booking.
     */
    public Booking assignEngineer(Long bookingId, AssignEngineerRequest req) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if (!"PENDING_ENGINEER_ASSIGNMENT".equals(booking.getStatus())
                && !"CONFLICT".equals(booking.getStatus())) {
            throw new RuntimeException("Booking must be in PENDING_ENGINEER_ASSIGNMENT or CONFLICT status.");
        }

        // Verify engineer is not on leave on that date
        List<?> leaves = leaveRepository.findApprovedLeavesOnDate(
                req.getEngineerEmployeeNumber(), booking.getDate());
        if (!leaves.isEmpty()) {
            throw new RuntimeException("Engineer " + req.getEngineerEmployeeNumber()
                    + " has approved leave on " + booking.getDate() + ". Please select a different engineer.");
        }

        // Verify engineer is not already booked for this slot
        long existingForEngineer = bookingRepository.findAll().stream()
                .filter(b -> req.getEngineerEmployeeNumber().equals(b.getEngineerEmployeeNumber())
                        && b.getDate().equals(booking.getDate())
                        && b.getSlot().equals(booking.getSlot())
                        && !"CANCELLED".equals(b.getStatus())
                        && !b.getId().equals(bookingId))
                .count();
        if (existingForEngineer > 0) {
            throw new RuntimeException("Engineer " + req.getEngineerEmployeeNumber()
                    + " already has a booking in the " + booking.getSlot() + " slot on " + booking.getDate());
        }

        booking.setEngineerEmployeeNumber(req.getEngineerEmployeeNumber());
        booking.setStatus("CONFIRMED");
        booking.setConflictReason(null);
        booking.setConflictDetectedAt(null);
        booking.setUpdatedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);
        logger.info("Booking {} confirmed — Engineer {} assigned by City Head {}",
                bookingId, req.getEngineerEmployeeNumber(), req.getCityHeadUserId());
        return saved;
    }

    /**
     * Cancel a booking.
     */
    public Booking cancelBooking(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));
        booking.setStatus("CANCELLED");
        booking.setUpdatedAt(Instant.now());
        return bookingRepository.save(booking);
    }

    /**
     * Mark an inspection as completed.
     */
    public Booking markInspectionDone(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));
        booking.setStatus("INSPECTION_DONE");
        booking.setUpdatedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);

        leadRepository.findById(booking.getLeadId()).ifPresent(lead -> {
            lead.setReportStatus("Inspection Done");
            leadRepository.save(lead);
        });
        return saved;
    }

    /**
     * Mark the report as validated but held back (e.g., waiting for payment).
     */
    public Booking markValidationDone(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));
        
        if (!"INSPECTION_DONE".equals(booking.getStatus()) && !"COMPLETED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking must be in INSPECTION_DONE status before validating.");
        }

        booking.setStatus("VALIDATION_DONE");
        booking.setUpdatedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);

        leadRepository.findById(booking.getLeadId()).ifPresent(lead -> {
            lead.setReportStatus("Validation Done");
            leadRepository.save(lead);
        });
        return saved;
    }

    /**
     * Mark the final report as sent to the customer.
     */
    public Booking markReportSent(Long bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));
        
        if (!"VALIDATION_DONE".equals(booking.getStatus()) && !"INSPECTION_DONE".equals(booking.getStatus()) && !"COMPLETED".equals(booking.getStatus())) {
            throw new RuntimeException("Booking must be validated before sending the report.");
        }

        booking.setStatus("REPORT_SENT");
        booking.setUpdatedAt(Instant.now());
        Booking saved = bookingRepository.save(booking);

        leadRepository.findById(booking.getLeadId()).ifPresent(lead -> {
            lead.setReportStatus("Report Sent");
            leadRepository.save(lead);
        });
        return saved;
    }

    /**
     * Reschedule a booking to a new date/slot/time.
     * Applies the same rules as booking creation:
     * - New date must be in the future
     * - Same slot time validation
     * - Capacity check on new slot (standard cities only)
     * - Non-standard cities always go back to PENDING_APPROVAL
     * - Customer with partial payment goes back to PENDING_APPROVAL
     * - Engineer assignment is cleared (city head must re-assign)
     * - CONFLICT and PENDING bookings can be rescheduled; CANCELLED cannot
     */
    public Booking rescheduleBooking(Long bookingId, RescheduleBookingRequest req) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new RuntimeException("Booking not found: " + bookingId));

        if ("CANCELLED".equals(booking.getStatus())) {
            throw new RuntimeException("Cannot reschedule a cancelled booking.");
        }

        LocalDate newDate = LocalDate.parse(req.getDate());
        if (newDate.isBefore(LocalDate.now())) {
            throw new RuntimeException("Cannot reschedule to a date in the past.");
        }

        // Validate slot time
        validateSlotTime(req.getSlot(), req.getSlotTime());

        boolean isStandardCity = STANDARD_CITIES.contains(booking.getCity());

        // Capacity check on new slot (standard cities only)
        if (isStandardCity) {
            int capacity = getAvailableCapacity(booking.getCity(), newDate, req.getSlot(), booking.getBookingType());
            if (capacity <= 0) {
                throw new RuntimeException(
                    "No engineers available in " + booking.getCity() + " on " + req.getDate()
                    + " for the " + req.getSlot() + " slot. Please choose a different date or time.");
            }
        }

        // Determine new status — same logic as booking creation
        User requester = userRepository.findById(req.getRequestedByUserId())
                .orElseThrow(() -> new RuntimeException("User not found: " + req.getRequestedByUserId()));
        String role = requester.getRole().name();

        String newStatus;

        if (!"CUSTOMER".equals(role)) {
            // Sales, City Head, Admin → straight to pending assignment
            newStatus = "PENDING_ENGINEER_ASSIGNMENT";
        } else {
            // Customer self-serve reschedule
            if (!isStandardCity) {
                newStatus = "PENDING_APPROVAL";
            } else {
                Lead lead = leadRepository.findById(booking.getLeadId())
                        .orElseThrow(() -> new RuntimeException("Lead not found: " + booking.getLeadId()));
                String paymentStatus = lead.getPaymentStatus();
                if ("Fully Paid".equals(paymentStatus)) {
                    newStatus = "PENDING_ENGINEER_ASSIGNMENT";
                } else if ("Partially Paid".equals(paymentStatus)) {
                    newStatus = "PENDING_APPROVAL";
                } else {
                    throw new RuntimeException("Rescheduling not allowed: payment status is " + paymentStatus);
                }
            }
        }

        // Clear engineer assignment — city head must re-assign for the new date
        String previousEngineer = booking.getEngineerEmployeeNumber();
        booking.setDate(newDate);
        booking.setSlot(req.getSlot());
        booking.setSlotTime(req.getSlotTime());
        booking.setStatus(newStatus);
        booking.setEngineerEmployeeNumber(null);  // reset
        booking.setConflictReason(null);
        booking.setConflictDetectedAt(null);
        booking.setUpdatedAt(Instant.now());

        Booking saved = bookingRepository.save(booking);

        // Update Lead fields to reflect the rescheduled booking date
        Lead leadToUpdate = leadRepository.findById(booking.getLeadId())
                .orElseThrow(() -> new RuntimeException("Lead not found: " + booking.getLeadId()));
        leadToUpdate.setInspectionDateAndTime(req.getDate() + " " + req.getSlotTime());
        leadRepository.save(leadToUpdate);

        logger.info("Booking {} rescheduled to {} {} {} by User {} | Previous engineer: {} cleared | New status: {}",
                bookingId, newDate, req.getSlot(), req.getSlotTime(),
                req.getRequestedByUserId(), previousEngineer, newStatus);
        return saved;
    }



    private void validateSlotTime(String slot, String slotTime) {
        if ("MORNING".equals(slot) && !VALID_MORNING_TIMES.contains(slotTime)) {
            throw new RuntimeException("Invalid morning slot time: " + slotTime
                + ". Valid times: " + VALID_MORNING_TIMES);
        }
        if ("EVENING".equals(slot) && !VALID_EVENING_TIMES.contains(slotTime)) {
            throw new RuntimeException("Invalid evening slot time: " + slotTime
                + ". Valid times: " + VALID_EVENING_TIMES);
        }
    }

    public EngineerLeaveRepository getEngineerLeaveRepository() {
        return leaveRepository;
    }

    private void validateBookingType(Lead lead, String bookingType) {
        if ("REINSPECTION".equals(bookingType)) {
            // Must have re-inspection plan — check the inspectionType field from Zoho CRM
            // Eligible plans: "Inspection+Re-Inspection" or "Inspection+Re-Inspection+Interior"
            String plan = lead.getInspectionType();
            boolean hasReInspectionPlan = plan != null && PLANS_WITH_REINSPECTION.contains(plan.trim());
            if (!hasReInspectionPlan) {
                throw new RuntimeException(
                    "This customer has not purchased a re-inspection plan. " +
                    "Their plan is: '" + (plan != null ? plan : "not set") + "'." +
                    " Only 'Inspection+Re-Inspection' or 'Inspection+Re-Inspection+Interior' plans allow re-inspection booking.");
            }
            // Prior inspection report must be delivered (reportStatus = "Yes")
            boolean inspectionDone = "Yes".equalsIgnoreCase(lead.getReportStatus());
            if (!inspectionDone) {
                throw new RuntimeException(
                    "Re-inspection can only be booked after the inspection report has been delivered. " +
                    "Current report status: '" + lead.getReportStatus() + "'.");
            }
            // Must have a prior INSPECTION booking in our system
            boolean priorInspectionExists = bookingRepository
                    .existsActiveBookingForLeadAndType(lead.getId(), "INSPECTION");
            if (!priorInspectionExists) {
                throw new RuntimeException("No prior inspection booking found for this customer.");
            }
        } else if ("INSPECTION".equals(bookingType)) {
            // Must not already have an active inspection booking
            boolean alreadyHasInspection = bookingRepository
                    .existsActiveBookingForLeadAndType(lead.getId(), "INSPECTION");
            if (alreadyHasInspection) {
                throw new RuntimeException("This customer already has an active inspection booking.");
            }
        } else {
            throw new RuntimeException("Invalid booking type: " + bookingType + ". Must be INSPECTION or REINSPECTION.");
        }
    }
}
