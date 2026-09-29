package com.propchk.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "leads")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Lead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String zohoLeadId;

    private String customerName;
    private String phone;
    private String email;
    private String city;
    private String projectName;
    private String flatNo;
    private String bhkType;
    
    @Column(length = 1000)
    private String inspectionType;
    private String inspectionDateAndTime;
    private String dealType;
    private String bookedBy;
    private String inspectionDoneBy;
    
    @Column(length = 2000)
    private String remarks;
    
    private String validatorName;
    private String digitalTwinTagging;
    private String reportStatus;
    private String emailApproval;
    private String leadOwner;
    // Frontend helpers
    @jakarta.persistence.Transient
    private String currentBookingStatus;

    @jakarta.persistence.Transient
    private String currentAssignedEngineer;

    // Payment fields (sourced from Zoho CRM financial data)
    private String estimateNo;
    private String retainerNo;
    private String retainerNo2;
    private Double inspectionCost;
    private Double reInspectionCost;
    private Double receivedAmount;
    private Double pendingAmount;
    private Double totalRevenue;
    
    // New exact payment / refund logic fields
    private String fullPaymentReceived;
    private String refund;
    private Double refundAmount;
    private String refundDate;
    private String refundReason;
    private String refundInitiatedFor;

    /**
     * Computed payment status:
     * "Refunded"        — if refundAmount > 0
     * "Fully Paid"      — if pendingAmount <= 100 (₹100 tolerance)
     * "Partially Paid"  — if receivedAmount > 0 but pendingAmount > 100
     * "Unpaid"          — if receivedAmount is null or 0
     */
    private String paymentStatus;

    private String zohoModifiedTime;

    private Instant syncedAt;
}
