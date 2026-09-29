package com.propchk.be.service;

import com.propchk.be.dto.EngineerSchedule;
import com.propchk.be.dto.SlotStatus;
import com.propchk.be.entity.Booking;
import com.propchk.be.entity.Engineer;
import com.propchk.be.entity.EngineerLeave;
import com.propchk.be.entity.Lead;
import com.propchk.be.repository.BookingRepository;
import com.propchk.be.repository.EngineerLeaveRepository;
import com.propchk.be.repository.EngineerRepository;
import com.propchk.be.repository.LeadRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScheduleService {

    private final EngineerRepository engineerRepository;
    private final EngineerLeaveRepository leaveRepository;
    private final BookingRepository bookingRepository;
    private final LeadRepository leadRepository;

    public ScheduleService(EngineerRepository engineerRepository,
                           EngineerLeaveRepository leaveRepository,
                           BookingRepository bookingRepository,
                           LeadRepository leadRepository) {
        this.engineerRepository = engineerRepository;
        this.leaveRepository = leaveRepository;
        this.bookingRepository = bookingRepository;
        this.leadRepository = leadRepository;
    }

    public List<EngineerSchedule> getEngineerMatrix(LocalDate startDate, LocalDate endDate, String city, String employeeNumber) {
        // 1. Fetch active engineers
        List<Engineer> engineers = engineerRepository.findByEmploymentStatusAndExitStatus(0, 0);

        // Apply filters
        if (city != null && !city.isBlank()) {
            engineers = engineers.stream().filter(e -> city.equalsIgnoreCase(e.getMappedCity())).collect(Collectors.toList());
        }
        if (employeeNumber != null && !employeeNumber.isBlank()) {
            engineers = engineers.stream().filter(e -> employeeNumber.equalsIgnoreCase(e.getEmployeeNumber())).collect(Collectors.toList());
        }

        if (engineers.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> empNums = engineers.stream().map(Engineer::getEmployeeNumber).collect(Collectors.toList());

        // 2. Fetch leaves for these engineers in date range
        List<EngineerLeave> leaves = leaveRepository.findApprovedLeavesInRange(empNums, startDate, endDate);

        // 3. Fetch confirmed/conflict bookings
        List<Booking> bookings = bookingRepository.findBookingsForEngineersInRange(empNums, startDate, endDate);

        // Fetch lead names for bookings
        List<Long> leadIds = bookings.stream().map(Booking::getLeadId).distinct().collect(Collectors.toList());
        Map<Long, String> leadNameMap = leadRepository.findAllById(leadIds).stream()
                .collect(Collectors.toMap(Lead::getId, l -> l.getCustomerName() != null ? l.getCustomerName() : "Unknown"));

        // Build mapping: empNum -> date -> slot -> Booking
        Map<String, Map<LocalDate, Map<String, Booking>>> bookingMap = new HashMap<>();
        for (Booking b : bookings) {
            bookingMap.computeIfAbsent(b.getEngineerEmployeeNumber(), k -> new HashMap<>())
                      .computeIfAbsent(b.getDate(), k -> new HashMap<>())
                      .put(b.getSlot(), b);
        }

        // Build mapping: empNum -> List<Leave>
        Map<String, List<EngineerLeave>> leaveMap = leaves.stream()
                .collect(Collectors.groupingBy(EngineerLeave::getEmployeeNumber));

        // 4. Construct matrix
        List<EngineerSchedule> matrix = new ArrayList<>();
        
        for (Engineer eng : engineers) {
            EngineerSchedule row = new EngineerSchedule();
            row.setEmployeeNumber(eng.getEmployeeNumber());
            row.setName(eng.getName());
            row.setCity(eng.getMappedCity());
            row.setJobTitle(eng.getJobTitle());

            Map<String, Map<String, SlotStatus>> scheduleMap = new LinkedHashMap<>();
            List<EngineerLeave> engLeaves = leaveMap.getOrDefault(eng.getEmployeeNumber(), Collections.emptyList());
            Map<LocalDate, Map<String, Booking>> engBookings = bookingMap.getOrDefault(eng.getEmployeeNumber(), Collections.emptyMap());

            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                LocalDate currentDate = date;
                
                // Check if on leave
                EngineerLeave leaveOnDate = engLeaves.stream()
                        .filter(l -> !l.getFromDate().isAfter(currentDate) && !l.getToDate().isBefore(currentDate))
                        .findFirst().orElse(null);

                Map<String, SlotStatus> slotMap = new LinkedHashMap<>();

                for (String slot : List.of("MORNING", "EVENING")) {
                    if (leaveOnDate != null) {
                        // Engineer is on leave this whole day (Keka leaves are full-day usually)
                        slotMap.put(slot, new SlotStatus("LEAVE", leaveOnDate.getLeaveTypeName(), null));
                    } else {
                        Booking booking = engBookings.getOrDefault(currentDate, Collections.emptyMap()).get(slot);
                        if (booking != null) {
                            String status = booking.getStatus(); // CONFIRMED or CONFLICT
                            String customerName = leadNameMap.getOrDefault(booking.getLeadId(), "Unknown Customer");
                            slotMap.put(slot, new SlotStatus(status, customerName, booking.getId()));
                        } else {
                            slotMap.put(slot, new SlotStatus("AVAILABLE", null, null));
                        }
                    }
                }
                scheduleMap.put(currentDate.toString(), slotMap);
            }
            row.setSchedule(scheduleMap);
            matrix.add(row);
        }

        return matrix;
    }
}
