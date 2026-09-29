package com.propchk.be.service;

import com.propchk.be.entity.Lead;
import com.propchk.be.entity.Role;
import com.propchk.be.entity.User;
import com.propchk.be.repository.LeadRepository;
import com.propchk.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ZohoLeadSyncService {

    private static final Logger logger = LoggerFactory.getLogger(ZohoLeadSyncService.class);

    private static final String ZOHO_LEADS_SEARCH_URL = "https://www.zohoapis.in/crm/v6/Leads/search";
    private static final int PER_PAGE = 200;

    private final LeadRepository leadRepository;
    private final ZohoTokenService zohoTokenService;
    private final RestTemplate restTemplate;
    private final UserRepository userRepository;
    private final ZohoLeadMapper leadMapper;

    public ZohoLeadSyncService(LeadRepository leadRepository,
                                ZohoTokenService zohoTokenService,
                                RestTemplate restTemplate,
                                UserRepository userRepository,
                                ZohoLeadMapper leadMapper) {
        this.leadRepository = leadRepository;
        this.zohoTokenService = zohoTokenService;
        this.restTemplate = restTemplate;
        this.userRepository = userRepository;
        this.leadMapper = leadMapper;
    }

    /**
     * Fetches all "Lead Won" records from Zoho CRM (paginated), upserts them into the local DB,
     * and returns the total count of records created or updated.
     * @param force if true, ignores the last-modified watermark and re-syncs from June 1, 2026
     */
    @SuppressWarnings("unchecked")
    public int syncLeadWonRecords(boolean force) {
        String accessToken = zohoTokenService.getAccessToken();
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + accessToken);
        HttpEntity<Void> httpEntity = new HttpEntity<>(headers);

        String lastModified;
        if (force) {
            lastModified = "2026-06-01T00:00:00+05:30";
            logger.info("Force sync enabled — starting from {}", lastModified);
        } else {
            lastModified = leadRepository.findTopByZohoModifiedTimeIsNotNullOrderByZohoModifiedTimeDesc()
                    .map(Lead::getZohoModifiedTime)
                    .orElse("2026-06-01T00:00:00+05:30");
            logger.info("Incremental sync — fetching leads modified after {}", lastModified);
        }

        int page = 1;
        int totalSynced = 0;
        boolean moreRecords = true;

        while (moreRecords) {
            String criteria = "((Lead_Status:equals:Lead Won)and(Modified_Time:greater_equal:" + lastModified + "))";
            String encodedCriteria = java.net.URLEncoder.encode(criteria, java.nio.charset.StandardCharsets.UTF_8);
            String urlStr = ZOHO_LEADS_SEARCH_URL + "?criteria=" + encodedCriteria + "&per_page=" + PER_PAGE + "&page=" + page;
            URI uri = URI.create(urlStr);

            logger.info("Fetching Zoho leads page {} from: {}", page, urlStr);

            ResponseEntity<Map> response = restTemplate.exchange(uri, HttpMethod.GET, httpEntity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body == null || !body.containsKey("data")) {
                logger.info("No 'data' key in Zoho response on page {}. Stopping sync.", page);
                break;
            }

            List<Map<String, Object>> records = (List<Map<String, Object>>) body.get("data");
            if (records == null || records.isEmpty()) {
                logger.info("Empty data on page {}. Stopping sync.", page);
                break;
            }

            java.util.List<Lead> leadsToSave = new java.util.ArrayList<>();
            for (Map<String, Object> record : records) {
                Lead saved = leadMapper.mapAndSave(record);
                if (saved != null) {
                    leadsToSave.add(saved);
                    totalSynced++;
                }
            }

            logger.info("Synced {} records on page {}.", leadsToSave.size(), page);

            // Check pagination
            Map<String, Object> info = (Map<String, Object>) body.get("info");
            if (info != null && Boolean.TRUE.equals(info.get("more_records"))) {
                page++;
            } else {
                moreRecords = false;
            }
        }

        logger.info("Zoho lead sync complete. Total records synced: {}", totalSynced);
        return totalSynced;
    }

    public Object getRawLeads() {
        String url = "https://www.zohoapis.in/crm/v6/Leads?per_page=5";
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + zohoTokenService.getAccessToken());
        HttpEntity<String> httpEntity = new HttpEntity<>(headers);
        ResponseEntity<Object> response = restTemplate.exchange(url, HttpMethod.GET, httpEntity, Object.class);
        return response.getBody();
    }
}
