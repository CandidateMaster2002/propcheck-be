package com.propchk.be.service;

import com.propchk.be.entity.Engineer;
import com.propchk.be.repository.EngineerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class KekaEngineerSyncService {

    private static final Logger logger = LoggerFactory.getLogger(KekaEngineerSyncService.class);
    private static final String EMPLOYEES_URL = "https://propright.keka.com/api/v1/hris/employees";
    private static final Set<String> ENGINEER_JOB_TITLES = Set.of("QC Inspector", "Sr. QC Inspector");

    /**
     * Maps Keka city names to our system's city names.
     * Keka → Our DB city name (from Zoho leads table)
     */
    private static final Map<String, String> CITY_MAP = Map.of(
            "New Delhi", "Delhi NCR",
            "Bangalore", "Bangalore",
            "Hyderabad", "Hyderabad",
            "Mumbai", "Mumbai",
            "Pune", "Pune",
            "Chennai", "Chennai"
    );

    private final EngineerRepository engineerRepository;
    private final KekaTokenService kekaTokenService;
    private final RestTemplate restTemplate;

    public KekaEngineerSyncService(EngineerRepository engineerRepository,
                                   KekaTokenService kekaTokenService,
                                   RestTemplate restTemplate) {
        this.engineerRepository = engineerRepository;
        this.kekaTokenService = kekaTokenService;
        this.restTemplate = restTemplate;
    }

    @SuppressWarnings("unchecked")
    public int syncEngineers() {
        String token = kekaTokenService.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + token);
        headers.set("Accept", "application/json");
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        List<Map<String, Object>> allEmployees = new ArrayList<>();
        String nextUrl = EMPLOYEES_URL;

        while (nextUrl != null) {
            ResponseEntity<Map> response = restTemplate.exchange(nextUrl, HttpMethod.GET, httpEntity, Map.class);
            Map<String, Object> body = response.getBody();
            if (body == null) break;

            List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
            if (data != null) allEmployees.addAll(data);

            nextUrl = (String) body.get("nextPage");
        }

        logger.info("Fetched {} total employees from Keka", allEmployees.size());

        int synced = 0;
        for (Map<String, Object> emp : allEmployees) {
            // Filter: must be Working + exitStatus None + correct job title
            int empStatus = getInt(emp, "employmentStatus");
            int exitStatus = getInt(emp, "exitStatus");
            String jobTitle = getJobTitle(emp);
            
            // Keka returns workerType as an Integer (0 = Permanent) or sometimes String
            Object workerTypeObj = emp.get("workerType");
            boolean isPermanent = false;
            if (workerTypeObj instanceof Number) {
                isPermanent = ((Number) workerTypeObj).intValue() == 0;
            } else if (workerTypeObj != null) {
                isPermanent = "Permanent".equalsIgnoreCase(workerTypeObj.toString());
            }

            if (empStatus != 0 || exitStatus != 0 || !isPermanent || !ENGINEER_JOB_TITLES.contains(jobTitle)) {
                continue;
            }

            String employeeNumber = str(emp, "employeeNumber");
            if (employeeNumber == null) continue;

            Engineer engineer = engineerRepository.findByEmployeeNumber(employeeNumber)
                    .orElseGet(Engineer::new);

            engineer.setEmployeeNumber(employeeNumber);
            engineer.setName(buildFullName(emp));
            engineer.setEmail(str(emp, "email") != null ? str(emp, "email") : str(emp, "workEmail"));
            engineer.setJobTitle(jobTitle);
            engineer.setEmploymentStatus(empStatus);
            engineer.setExitStatus(exitStatus);

            String kekaCity = getCity(emp);
            engineer.setKekaCity(kekaCity);
            engineer.setMappedCity(CITY_MAP.getOrDefault(kekaCity, kekaCity));
            engineer.setSyncedAt(Instant.now());

            engineerRepository.save(engineer);
            synced++;
        }

        logger.info("Engineer sync complete. {} QC engineers saved.", synced);
        return synced;
    }

    private String buildFullName(Map<String, Object> emp) {
        return (str(emp, "firstName") + " " + str(emp, "middleName") + " " + str(emp, "lastName"))
                .replaceAll("\\s+", " ").trim();
    }

    private String getJobTitle(Map<String, Object> emp) {
        Object jt = emp.get("jobTitle");
        if (jt instanceof Map) return str((Map<String, Object>) jt, "title");
        return jt != null ? jt.toString() : "";
    }

    private String getCity(Map<String, Object> emp) {
        if (emp.get("city") != null) return emp.get("city").toString();
        Object addr = emp.get("currentAddress");
        if (addr instanceof Map) return str((Map<String, Object>) addr, "city");
        return "";
    }

    private String str(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString() : "";
    }

    private int getInt(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return -1;
        return ((Number) val).intValue();
    }
}
