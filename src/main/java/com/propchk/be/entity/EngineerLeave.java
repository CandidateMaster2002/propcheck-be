package com.propchk.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "engineer_leaves")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EngineerLeave {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** References Engineer.employeeNumber */
    @Column(nullable = false)
    private String employeeNumber;

    private String leaveTypeName;

    @Column(nullable = false)
    private LocalDate fromDate;

    @Column(nullable = false)
    private LocalDate toDate;

    /** Approved / Pending / Rejected */
    private String status;

    private LocalDate requestedOn;

    private Instant syncedAt;
}
