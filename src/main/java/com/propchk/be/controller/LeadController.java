package com.propchk.be.controller;

import com.propchk.be.entity.Lead;
import com.propchk.be.repository.LeadRepository;
import com.propchk.be.service.ZohoLeadSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/leads")
public class LeadController {

    private final ZohoLeadSyncService zohoLeadSyncService;
    private final LeadRepository leadRepository;
    private final com.propchk.be.repository.BookingRepository bookingRepository;

    public LeadController(ZohoLeadSyncService zohoLeadSyncService, 
                          LeadRepository leadRepository,
                          com.propchk.be.repository.BookingRepository bookingRepository) {
        this.zohoLeadSyncService = zohoLeadSyncService;
        this.leadRepository = leadRepository;
        this.bookingRepository = bookingRepository;
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, Integer>> syncLeads(
            @RequestParam(required = false, defaultValue = "false") boolean force) {
        int count = zohoLeadSyncService.syncLeadWonRecords(force);
        return ResponseEntity.ok(Map.of("synced", count));
    }

    @GetMapping("/debug")
    public ResponseEntity<Object> debugLeads() {
        return ResponseEntity.ok(zohoLeadSyncService.getRawLeads());
    }

    @GetMapping
    public ResponseEntity<List<Lead>> listLeads(
            @RequestParam(required = false) String city,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "ALL") String bookingFilter,
            @RequestParam(required = false) Boolean unbookedOnly,
            @RequestParam(required = false) String owner) {
        
        String finalCity = (city != null && !city.trim().isEmpty()) ? city.trim() : "";
        String finalSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : "";
        String finalOwner = (owner != null && !owner.trim().isEmpty()) ? owner.trim() : "";
        
        // Backwards compatibility for the checkbox if the FE still sends it
        String finalFilter = bookingFilter;
        if (Boolean.TRUE.equals(unbookedOnly)) {
            finalFilter = "UNBOOKED";
        }
        
        List<Lead> leads = leadRepository.searchLeads(finalCity, finalSearch, finalFilter, finalOwner);

        // Fetch active bookings for these leads to populate UI status dynamically
        if (!leads.isEmpty()) {
            java.util.List<Long> leadIds = leads.stream().map(Lead::getId).collect(java.util.stream.Collectors.toList());
            java.util.List<com.propchk.be.entity.Booking> activeBookings = bookingRepository.findActiveBookingsByLeadIds(leadIds);
            
            java.util.Map<Long, com.propchk.be.entity.Booking> bookingMap = new java.util.HashMap<>();
            for (com.propchk.be.entity.Booking b : activeBookings) {
                // If a lead has multiple active bookings somehow, we prefer CONFIRMED
                if (!bookingMap.containsKey(b.getLeadId()) || "CONFIRMED".equals(b.getStatus())) {
                    bookingMap.put(b.getLeadId(), b);
                }
            }

            for (Lead l : leads) {
                com.propchk.be.entity.Booking b = bookingMap.get(l.getId());
                if (b != null) {
                    l.setCurrentBookingStatus(b.getStatus());
                    l.setCurrentAssignedEngineer(b.getEngineerEmployeeNumber());
                    
                    // Fallback to fill old leads where inspectionDateAndTime wasn't originally set during booking
                    if (l.getInspectionDateAndTime() == null || l.getInspectionDateAndTime().trim().isEmpty()) {
                        l.setInspectionDateAndTime(b.getDate() + " " + b.getSlotTime());
                    }
                } else {
                    l.setCurrentBookingStatus("UNBOOKED");
                }
            }
        }

        return ResponseEntity.ok(leads);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lead> getLeadById(@PathVariable Long id) {
        return leadRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/me")
    public ResponseEntity<Lead> getMyLeadData(@RequestParam String email) {
        return leadRepository.findByEmail(email)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/remarks")
    public ResponseEntity<Lead> updateRemarks(@PathVariable Long id, @org.springframework.web.bind.annotation.RequestBody java.util.Map<String, String> payload) {
        return leadRepository.findById(id).map(lead -> {
            lead.setRemarks(payload.get("remarks"));
            return ResponseEntity.ok(leadRepository.save(lead));
        }).orElse(ResponseEntity.notFound().build());
    }
}
