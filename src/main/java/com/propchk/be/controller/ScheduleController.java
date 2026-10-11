package com.propchk.be.controller;

import com.propchk.be.dto.EngineerSchedule;
import com.propchk.be.service.ScheduleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/schedule")
public class ScheduleController {

    private final ScheduleService scheduleService;

    public ScheduleController(ScheduleService scheduleService) {
        this.scheduleService = scheduleService;
    }

    /**
     * Gets the full engineer schedule matrix (Leaves and Bookings).
     * Used by Admin, City Head, and Sales for visibility.
     * 
     * GET /api/schedule/matrix?startDate=2026-09-25&endDate=2026-10-02&city=Hyderabad
     */
    @GetMapping("/matrix")
    public ResponseEntity<List<EngineerSchedule>> getMatrix(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String employeeNumber) {
            
        List<EngineerSchedule> matrix = scheduleService.getEngineerMatrix(startDate, endDate, city, employeeNumber);
        return ResponseEntity.ok(matrix);
    }

    /**
     * Trigger the daily morning briefing Adaptive Card to Microsoft Teams.
     * POST /api/schedule/send-briefing?date=2026-10-11
     */
    @org.springframework.web.bind.annotation.PostMapping("/send-briefing")
    public ResponseEntity<java.util.Map<String, Object>> sendBriefing(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        scheduleService.sendMorningBriefing(date != null ? date : LocalDate.now());
        return ResponseEntity.ok(java.util.Map.of("status", "ok", "message", "Daily briefing sent to Teams!"));
    }

    /**
     * Feature 6: Trigger the weekly management digest Adaptive Card to Microsoft Teams.
     * POST /api/schedule/send-weekly-digest
     */
    @org.springframework.web.bind.annotation.PostMapping("/send-weekly-digest")
    public ResponseEntity<java.util.Map<String, Object>> sendWeeklyDigest() {
        scheduleService.sendWeeklyManagementDigest();
        return ResponseEntity.ok(java.util.Map.of("status", "ok", "message", "Weekly management digest sent to Teams!"));
    }
}
