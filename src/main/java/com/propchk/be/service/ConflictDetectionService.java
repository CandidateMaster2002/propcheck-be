package com.propchk.be.service;

import com.propchk.be.entity.Booking;
import com.propchk.be.entity.EngineerLeave;
import com.propchk.be.repository.BookingRepository;
import com.propchk.be.repository.EngineerLeaveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@Service
public class ConflictDetectionService {

    private static final Logger logger = LoggerFactory.getLogger(ConflictDetectionService.class);

    private final BookingRepository bookingRepository;
    private final EngineerLeaveRepository leaveRepository;
    private final com.propchk.be.repository.LeadRepository leadRepository;
    private final com.propchk.be.repository.EngineerRepository engineerRepository;
    private final TeamsNotificationService teamsNotificationService;

    public ConflictDetectionService(BookingRepository bookingRepository,
                                    EngineerLeaveRepository leaveRepository,
                                    com.propchk.be.repository.LeadRepository leadRepository,
                                    com.propchk.be.repository.EngineerRepository engineerRepository,
                                    TeamsNotificationService teamsNotificationService) {
        this.bookingRepository = bookingRepository;
        this.leaveRepository = leaveRepository;
        this.leadRepository = leadRepository;
        this.engineerRepository = engineerRepository;
        this.teamsNotificationService = teamsNotificationService;
    }

    /**
     * Scans all CONFIRMED future bookings.
     * If the assigned engineer now has an approved leave on the booking date,
     * marks the booking as CONFLICT.
     *
     * @return number of conflicts newly detected
     */
    public int detectAndMarkConflicts() {
        LocalDate today = LocalDate.now();

        // Fetch all CONFIRMED bookings from today onwards
        List<Booking> confirmedBookings = bookingRepository.findAll().stream()
                .filter(b -> "CONFIRMED".equals(b.getStatus())
                        && b.getDate() != null
                        && !b.getDate().isBefore(today)
                        && b.getEngineerEmployeeNumber() != null)
                .toList();

        int conflictCount = 0;

        for (Booking booking : confirmedBookings) {
            List<EngineerLeave> leaves = leaveRepository.findApprovedLeavesOnDate(
                    booking.getEngineerEmployeeNumber(), booking.getDate());

            if (!leaves.isEmpty()) {
                String reason = "Engineer " + booking.getEngineerEmployeeNumber()
                        + " has approved leave on " + booking.getDate()
                        + " (" + leaves.get(0).getLeaveTypeName() + ")";

                booking.setStatus("CONFLICT");
                booking.setConflictReason(reason);
                booking.setConflictDetectedAt(Instant.now());
                booking.setUpdatedAt(Instant.now());
                bookingRepository.save(booking);

                logger.warn("CONFLICT detected — Booking ID {} | Lead {} | Date {} | Engineer {} | Reason: {}",
                        booking.getId(), booking.getLeadId(), booking.getDate(),
                        booking.getEngineerEmployeeNumber(), reason);

                // Push urgent real-time alert card to Microsoft Teams
                try {
                    com.propchk.be.entity.Lead lead = leadRepository.findById(booking.getLeadId()).orElse(null);
                    String engineerName = engineerRepository.findByEmployeeNumber(booking.getEngineerEmployeeNumber())
                            .map(com.propchk.be.entity.Engineer::getName)
                            .orElse(booking.getEngineerEmployeeNumber());
                    String leaveType = leaves.get(0).getLeaveTypeName();

                    teamsNotificationService.sendConflictAlertNotification(booking, lead, engineerName, leaveType, reason);
                } catch (Exception e) {
                    logger.error("Failed to push Teams conflict alert for booking {}: {}", booking.getId(), e.getMessage());
                }

                conflictCount++;
            }
        }

        if (conflictCount > 0) {
            logger.warn("Conflict detection complete. {} new conflict(s) found.", conflictCount);
        } else {
            logger.info("Conflict detection complete. No new conflicts.");
        }

        return conflictCount;
    }
}
