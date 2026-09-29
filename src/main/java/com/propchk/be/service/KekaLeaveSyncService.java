package com.propchk.be.service;

import com.propchk.be.entity.EngineerLeave;
import com.propchk.be.repository.EngineerLeaveRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KekaLeaveSyncService {

    private static final Logger logger = LoggerFactory.getLogger(KekaLeaveSyncService.class);
    private static final String LEAVE_REQUESTS_URL = "https://propright.keka.com/api/v1/time/leaverequests";
    private static final String LEAVE_TYPES_URL = "https://propright.keka.com/api/v1/time/leavetypes";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final EngineerLeaveRepository leaveRepository;
    private final KekaTokenService kekaTokenService;
    private final RestTemplate restTemplate;

    public KekaLeaveSyncService(EngineerLeaveRepository leaveRepository,
                                KekaTokenService kekaTokenService,
                                RestTemplate restTemplate) {
        this.leaveRepository = leaveRepository;
        this.kekaTokenService = kekaTokenService;
        this.restTemplate = restTemplate;
    }

    /**
     * Syncs leave requests for today → today+89 days (Keka max 90-day window).
     * Clears existing future leaves before re-syncing to stay fresh.
     */
    @SuppressWarnings("unchecked")
    public int syncLeaves() {
        String token = kekaTokenService.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/json");
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        // Fetch leave type map (id → name)
        Map<String, String> leaveTypeMap = fetchLeaveTypeMap(httpEntity);

        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(89);
        String fromDate = today.format(DATE_FMT);
        String toDate = endDate.format(DATE_FMT);

        logger.info("Syncing Keka leaves from {} to {}", fromDate, toDate);

        List<Map<String, Object>> allLeaves = new ArrayList<>();
        int pageNumber = 1;

        while (true) {
            String url = LEAVE_REQUESTS_URL + "?from=" + fromDate + "&to=" + toDate
                    + "&pageNumber=" + pageNumber + "&pageSize=200";

            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) break;

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data != null) allLeaves.addAll(data);

            if (body.get("nextPage") == null) break;
            pageNumber++;
        }

        logger.info("Fetched {} leave records from Keka", allLeaves.size());

        // Delete all existing leaves in the sync window and replace
        leaveRepository.deleteAll(leaveRepository.findAllApprovedLeavesOnDate(today));
        leaveRepository.flush();

        List<EngineerLeave> toSave = new ArrayList<>();
        for (Map<String, Object> lv : allLeaves) {
            String status = str(lv, "status");
            // Only store Approved or Pending leaves
            if (!"Approved".equalsIgnoreCase(status) && !"Pending".equalsIgnoreCase(status)) continue;

            EngineerLeave leave = new EngineerLeave();
            leave.setEmployeeNumber(str(lv, "employeeNumber"));
            leave.setLeaveTypeName(leaveTypeMap.getOrDefault(str(lv, "leaveTypeId"), str(lv, "leaveTypeId")));
            leave.setFromDate(parseDate(str(lv, "fromDate")));
            leave.setToDate(parseDate(str(lv, "toDate")));
            leave.setStatus(status);
            leave.setRequestedOn(parseDate(str(lv, "requestedOn")));
            leave.setSyncedAt(Instant.now());
            toSave.add(leave);
        }

        leaveRepository.saveAll(toSave);
        logger.info("Keka leave sync complete. {} leaves saved.", toSave.size());
        return toSave.size();
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> fetchLeaveTypeMap(HttpEntity<Void> httpEntity) {
        Map<String, String> leaveTypeMap = new HashMap<>();
        try {
            ResponseEntity<Map> response = restTemplate.exchange(LEAVE_TYPES_URL, HttpMethod.GET, httpEntity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body != null) {
                List<Map<String, Object>> types = (List<Map<String, Object>>) body.get("data");
                if (types != null) {
                    types.forEach(t -> leaveTypeMap.put(str(t, "id"), str(t, "name")));
                }
            }
        } catch (Exception e) {
            logger.warn("Could not fetch leave types: {}", e.getMessage());
        }
        return leaveTypeMap;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || dateStr.isBlank()) return null;
        try {
            // Keka returns ISO timestamps like "2026-10-01T00:00:00+05:30", extract the date part
            return LocalDate.parse(dateStr.length() > 10 ? dateStr.substring(0, 10) : dateStr);
        } catch (Exception e) {
            return null;
        }
    }

    private String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }
}
