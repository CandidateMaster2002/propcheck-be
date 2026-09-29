package com.propchk.be.service;

import com.propchk.be.entity.Lead;
import com.propchk.be.entity.Role;
import com.propchk.be.entity.User;
import com.propchk.be.repository.LeadRepository;
import com.propchk.be.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Shared Zoho → Lead mapping logic.
 *
 * Extracted from ZohoLeadSyncService so both the bulk-sync flow and the
 * webhook receiver can call it without duplicating code.
 */
@Component
public class ZohoLeadMapper {

    private static final Logger logger = LoggerFactory.getLogger(ZohoLeadMapper.class);

    private final LeadRepository leadRepository;
    private final UserRepository userRepository;

    public ZohoLeadMapper(LeadRepository leadRepository, UserRepository userRepository) {
        this.leadRepository = leadRepository;
        this.userRepository = userRepository;
    }

    /**
     * Maps a single raw Zoho CRM record (as returned by the v6 API) onto a Lead entity
     * (upsert: existing record is updated, new record is created), then persists it.
     *
     * Also auto-creates a CUSTOMER user account for the lead's email address if one
     * doesn't already exist.
     *
     * @param record  the raw field map from the Zoho API response
     * @return the saved Lead entity
     */
    public Lead mapAndSave(Map<String, Object> record) {
        String zohoId = stringOrNull(record, "id");
        if (zohoId == null) {
            logger.warn("Record has no 'id' field — skipping. Record keys: {}", record.keySet());
            return null;
        }

        Lead lead = leadRepository.findByZohoLeadId(zohoId).orElseGet(Lead::new);

        lead.setZohoLeadId(zohoId);
        lead.setCustomerName(stringOrNull(record, "Full_Name"));
        lead.setPhone(stringOrNull(record, "Phone"));
        lead.setEmail(stringOrNull(record, "Email"));
        lead.setCity(stringOrNull(record, "City1"));
        lead.setProjectName(stringOrNull(record, "Project_Name"));
        lead.setFlatNo(stringOrNull(record, "Unit_No"));
        lead.setBhkType(stringOrNull(record, "Unit_Type1"));

        lead.setInspectionType(stringOrNull(record, "param"));
        lead.setInspectionDateAndTime(stringOrNull(record, "Scheduled_date_and_time_of_inspection"));
        lead.setDealType(stringOrNull(record, "Property_Type"));
        lead.setBookedBy(extractNameFromMap(record.get("Created_By")));
        lead.setLeadOwner(extractNameFromMap(record.get("Owner")));
        lead.setInspectionDoneBy(stringOrNull(record, "Inspected_By"));
        lead.setRemarks(stringOrNull(record, "Remarks"));
        lead.setValidatorName(extractNameFromMap(record.get("Report_Validated_By")));
        lead.setDigitalTwinTagging(stringOrNull(record, "Digital_Twin"));
        lead.setReportStatus(stringOrNull(record, "Inspection_Report_Sent"));
        lead.setEmailApproval(stringOrNull(record, "Inspection_Email_Status"));
        lead.setZohoModifiedTime(stringOrNull(record, "Modified_Time"));

        // Payment fields
        lead.setEstimateNo(stringOrNull(record, "Estimate_No"));
        lead.setRetainerNo(stringOrNull(record, "Retainer_No"));
        lead.setRetainerNo2(stringOrNull(record, "Retainer_No_2"));
        lead.setInspectionCost(doubleOrNull(record, "Inspection_Cost"));
        lead.setReInspectionCost(doubleOrNull(record, "Re_Inspection_Cost"));
        lead.setReceivedAmount(doubleOrNull(record, "Received_Amount"));
        lead.setPendingAmount(doubleOrNull(record, "Pending_Amt"));
        lead.setTotalRevenue(doubleOrNull(record, "Revenue"));

        lead.setFullPaymentReceived(stringOrNull(record, "Full_Payment_Received"));
        lead.setRefund(stringOrNull(record, "Refund"));
        lead.setRefundAmount(doubleOrNull(record, "Refund_Amount"));
        lead.setRefundDate(stringOrNull(record, "Refund_Date"));
        lead.setRefundReason(stringOrNull(record, "Refund_Reason"));
        lead.setRefundInitiatedFor(stringOrNull(record, "Refund_Initiated_For"));

        lead.setPaymentStatus(computePaymentStatus(
                lead.getRefund(),
                lead.getFullPaymentReceived(),
                lead.getRefundAmount(),
                lead.getReceivedAmount(),
                lead.getPendingAmount()
        ));

        lead.setSyncedAt(Instant.now());

        Lead saved = leadRepository.save(lead);

        // Auto-create a CUSTOMER portal account for this lead if one doesn't exist yet
        if (saved.getEmail() != null && !saved.getEmail().trim().isEmpty()) {
            String email = saved.getEmail().trim();
            if (userRepository.findByEmail(email).isEmpty()) {
                User user = new User();
                user.setName(saved.getCustomerName() != null ? saved.getCustomerName() : "Customer");
                user.setEmail(email);
                user.setPassword("123password"); // Default fixed password — customer must reset
                user.setRole(Role.CUSTOMER);
                user.setCity(saved.getCity());
                userRepository.save(user);
                logger.info("Auto-created CUSTOMER account for email: {}", email);
            }
        }

        return saved;
    }

    // ─── helper utilities ────────────────────────────────────────────────────

    public String stringOrNull(Map<String, Object> record, String key) {
        Object val = record.get(key);
        return val != null ? val.toString() : null;
    }

    public Double doubleOrNull(Map<String, Object> record, String key) {
        Object val = record.get(key);
        if (val == null) return null;
        if (val instanceof Number) return ((Number) val).doubleValue();
        try { return Double.parseDouble(val.toString()); } catch (NumberFormatException e) { return null; }
    }

    public String extractNameFromMap(Object obj) {
        if (obj instanceof Map) {
            Object name = ((Map<?, ?>) obj).get("name");
            return name != null ? name.toString() : null;
        }
        return obj != null ? obj.toString() : null;
    }

    public String computePaymentStatus(String refundFlag, String fullPaymentReceivedFlag,
                                       Double refund, Double received, Double pending) {
        
        if (refundFlag != null && (refundFlag.trim().equalsIgnoreCase("Yes") || refundFlag.trim().equalsIgnoreCase("true"))) {
            return "Refunded";
        }
        if (refund != null && refund > 0) {
            return "Refunded";
        }
        
        if (fullPaymentReceivedFlag != null && (fullPaymentReceivedFlag.trim().equalsIgnoreCase("Yes") || fullPaymentReceivedFlag.trim().equalsIgnoreCase("true"))) {
            return "Fully Paid";
        }
        
        if (pending != null && pending <= 100) return "Fully Paid";
        if (received != null && received > 0) return "Partially Paid";
        return "Unpaid";
    }
}
