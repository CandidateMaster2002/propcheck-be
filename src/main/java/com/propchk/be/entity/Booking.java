package com.propchk.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** FK to Lead.id */
    @Column(nullable = false)
    private Long leadId;

    /** INSPECTION or REINSPECTION */
    @Column(nullable = false)
    private String bookingType;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private LocalDate date;

    /** MORNING or EVENING */
    @Column(nullable = false)
    private String slot;

    /** "10:00", "10:30", "11:00", "11:30", "14:00", "14:30", "15:00", "15:30", "16:00" */
    @Column(nullable = false)
    private String slotTime;

    /** Set by City Head — references Engineer.employeeNumber */
    private String engineerEmployeeNumber;

    /**
     * PENDING_APPROVAL — customer with partial payment, awaiting sales approval
     * PENDING_ENGINEER_ASSIGNMENT — approved, awaiting city head to assign engineer
     * CONFIRMED — engineer assigned
     * CONFLICT — assigned engineer has a leave on this date
     * CANCELLED
     */
    @Column(nullable = false)
    private String status;

    /** FK to User.id — who created the booking (sales or customer) */
    @Column(nullable = false)
    private Long createdByUserId;

    /** FK to User.id — sales person who approved PENDING_APPROVAL */
    private Long approvedByUserId;

    /** Populated when status = CONFLICT */
    @Column(length = 1000)
    private String conflictReason;

    private Instant conflictDetectedAt;

    /** FK to User.id - who is currently scheduled/re-scheduled the booking */
    private Long scheduledByUserId;

    private Instant createdAt;
    private Instant updatedAt;
}
