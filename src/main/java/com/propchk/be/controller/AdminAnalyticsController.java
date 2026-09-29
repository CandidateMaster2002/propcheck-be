package com.propchk.be.controller;

import com.propchk.be.entity.Engineer;
import com.propchk.be.repository.BookingRepository;
import com.propchk.be.repository.EngineerRepository;
import com.propchk.be.repository.LeadRepository;
import com.propchk.be.repository.UserRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
public class AdminAnalyticsController {

    private final BookingRepository bookingRepository;
    private final LeadRepository leadRepository;
    private final EngineerRepository engineerRepository;
    private final UserRepository userRepository;

    public AdminAnalyticsController(BookingRepository bookingRepository,
                                    LeadRepository leadRepository,
                                    EngineerRepository engineerRepository,
                                    UserRepository userRepository) {
        this.bookingRepository = bookingRepository;
        this.leadRepository = leadRepository;
        this.engineerRepository = engineerRepository;
        this.userRepository = userRepository;
    }

    /**
     * Main dashboard summary card.
     * Returns top-level counts for Admin / Sales / City Head dashboards.
     *
     * GET /api/admin/summary
     */
    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> getSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();

        // Leads
        summary.put("totalLeads", leadRepository.count());

        // Bookings by status
        Map<String, Long> bookingsByStatus = new LinkedHashMap<>();
        bookingRepository.countByStatus()
                .forEach(row -> bookingsByStatus.put((String) row[0], (Long) row[1]));
        summary.put("bookingsByStatus", bookingsByStatus);
        summary.put("totalBookings", bookingsByStatus.values().stream().mapToLong(Long::longValue).sum());
        summary.put("upcomingBookings", bookingRepository.countUpcomingBookings(LocalDate.now()));
        summary.put("openConflicts", bookingsByStatus.getOrDefault("CONFLICT", 0L));
        summary.put("pendingApproval", bookingsByStatus.getOrDefault("PENDING_APPROVAL", 0L));
        summary.put("pendingEngineerAssignment", bookingsByStatus.getOrDefault("PENDING_ENGINEER_ASSIGNMENT", 0L));

        // Bookings by type
        Map<String, Long> bookingsByType = new LinkedHashMap<>();
        bookingRepository.countByBookingType()
                .forEach(row -> bookingsByType.put((String) row[0], (Long) row[1]));
        summary.put("bookingsByType", bookingsByType);

        // Engineers
        long totalEngineers = engineerRepository.findByEmploymentStatusAndExitStatus(0, 0).size();
        summary.put("totalActiveEngineers", totalEngineers);

        // Users by role
        Map<String, Long> usersByRole = new LinkedHashMap<>();
        userRepository.findAll().forEach(u ->
                usersByRole.merge(u.getRole().name(), 1L, Long::sum));
        summary.put("usersByRole", usersByRole);

        return ResponseEntity.ok(summary);
    }

    /**
     * City-wise breakdown.
     * Returns bookings count per city, engineers per city.
     *
     * GET /api/admin/city-breakdown
     */
    @GetMapping("/city-breakdown")
    public ResponseEntity<List<Map<String, Object>>> getCityBreakdown() {
        // Bookings per city and status
        Map<String, Map<String, Long>> cityStatusMap = new LinkedHashMap<>();
        bookingRepository.countByCityAndStatus().forEach(row -> {
            String city = (String) row[0];
            String status = (String) row[1];
            long count = (Long) row[2];
            cityStatusMap.computeIfAbsent(city, k -> new LinkedHashMap<>()).put(status, count);
        });

        // Engineers per city
        Map<String, Long> engineersByCity = engineerRepository
                .findByEmploymentStatusAndExitStatus(0, 0)
                .stream()
                .collect(Collectors.groupingBy(Engineer::getMappedCity, Collectors.counting()));

        Set<String> allCities = new LinkedHashSet<>();
        allCities.addAll(cityStatusMap.keySet());
        allCities.addAll(engineersByCity.keySet());

        List<Map<String, Object>> result = allCities.stream().map(city -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("city", city);
            row.put("engineers", engineersByCity.getOrDefault(city, 0L));
            Map<String, Long> statuses = cityStatusMap.getOrDefault(city, Map.of());
            row.put("confirmed", statuses.getOrDefault("CONFIRMED", 0L));
            row.put("pendingAssignment", statuses.getOrDefault("PENDING_ENGINEER_ASSIGNMENT", 0L));
            row.put("pendingApproval", statuses.getOrDefault("PENDING_APPROVAL", 0L));
            row.put("conflicts", statuses.getOrDefault("CONFLICT", 0L));
            row.put("cancelled", statuses.getOrDefault("CANCELLED", 0L));
            row.put("total", statuses.values().stream().mapToLong(Long::longValue).sum());
            return row;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Bookings for a specific date (daily calendar view for City Head).
     *
     * GET /api/admin/bookings-by-date?date=2026-10-15
     */
    @GetMapping("/bookings-by-date")
    public ResponseEntity<Object> getBookingsByDate(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        var bookings = bookingRepository.findByDateOrderByCityAscSlotAsc(date);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("date", date.toString());
        result.put("totalBookings", bookings.size());
        result.put("bookings", bookings);
        return ResponseEntity.ok(result);
    }

    /**
     * Bookings in a date range (weekly/monthly view).
     *
     * GET /api/admin/bookings-range?from=2026-10-01&to=2026-10-31
     */
    @GetMapping("/bookings-range")
    public ResponseEntity<Object> getBookingsInRange(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        var bookings = bookingRepository.findInDateRange(from, to);

        // Group by date for calendar rendering
        Map<String, List<Object>> groupedByDate = new LinkedHashMap<>();
        bookings.forEach(b -> {
            String dateKey = b.getDate().toString();
            groupedByDate.computeIfAbsent(dateKey, k -> new ArrayList<>()).add(b);
        });

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("from", from.toString());
        result.put("to", to.toString());
        result.put("totalBookings", bookings.size());
        result.put("byDate", groupedByDate);
        return ResponseEntity.ok(result);
    }

    /**
     * Payment analytics from leads data.
     * Returns breakdown of payment status across all customers.
     *
     * GET /api/admin/payment-summary
     */
    @GetMapping("/payment-summary")
    public ResponseEntity<Map<String, Object>> getPaymentSummary() {
        var leads = leadRepository.findAll();

        Map<String, Long> byPaymentStatus = new LinkedHashMap<>();
        double totalRevenue = 0;
        double totalReceived = 0;
        double totalPending = 0;
        double totalRefunded = 0;

        for (var lead : leads) {
            String ps = lead.getPaymentStatus() != null ? lead.getPaymentStatus() : "Unknown";
            byPaymentStatus.merge(ps, 1L, Long::sum);

            if (lead.getTotalRevenue() != null) totalRevenue += lead.getTotalRevenue();
            if (lead.getReceivedAmount() != null) totalReceived += lead.getReceivedAmount();
            if (lead.getPendingAmount() != null) totalPending += lead.getPendingAmount();
            if (lead.getRefundAmount() != null) totalRefunded += lead.getRefundAmount();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCustomers", leads.size());
        result.put("byPaymentStatus", byPaymentStatus);
        result.put("totalRevenue", Math.round(totalRevenue * 100.0) / 100.0);
        result.put("totalReceived", Math.round(totalReceived * 100.0) / 100.0);
        result.put("totalPending", Math.round(totalPending * 100.0) / 100.0);
        result.put("totalRefunded", Math.round(totalRefunded * 100.0) / 100.0);

        return ResponseEntity.ok(result);
    }

    /**
     * Engineer utilisation — how many bookings each engineer has (upcoming).
     *
     * GET /api/admin/engineer-utilisation?city=Hyderabad (optional)
     */
    @GetMapping("/engineer-utilisation")
    public ResponseEntity<List<Map<String, Object>>> getEngineerUtilisation(
            @RequestParam(required = false) String city) {
        List<Engineer> engineers = city != null
                ? engineerRepository.findByMappedCityAndEmploymentStatusAndExitStatus(city, 0, 0)
                : engineerRepository.findByEmploymentStatusAndExitStatus(0, 0);

        LocalDate today = LocalDate.now();

        List<Map<String, Object>> result = engineers.stream().map(eng -> {
            long upcoming = bookingRepository.findConfirmedFutureBookingsForEngineer(
                    eng.getEmployeeNumber(), today).size();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("employeeNumber", eng.getEmployeeNumber());
            row.put("name", eng.getName());
            row.put("city", eng.getMappedCity());
            row.put("jobTitle", eng.getJobTitle());
            row.put("upcomingConfirmedBookings", upcoming);
            return row;
        }).sorted(Comparator.comparingLong(
                (Map<String, Object> m) -> (Long) m.get("upcomingConfirmedBookings")).reversed())
          .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    /**
     * Inspection type distribution across all leads.
     *
     * GET /api/admin/inspection-type-breakdown
     */
    @GetMapping("/inspection-type-breakdown")
    public ResponseEntity<Map<String, Object>> getInspectionTypeBreakdown() {
        Map<String, Long> byType = new LinkedHashMap<>();
        leadRepository.findAll().forEach(lead -> {
            String type = lead.getInspectionType() != null ? lead.getInspectionType() : "Not Set";
            byType.merge(type, 1L, Long::sum);
        });
        return ResponseEntity.ok(Map.of("byInspectionType", byType));
    }
}
