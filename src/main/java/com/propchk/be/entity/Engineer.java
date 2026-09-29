package com.propchk.be.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "engineers")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Engineer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String employeeNumber;

    private String name;
    private String email;

    /** Raw city name from Keka e.g. "New Delhi" */
    private String kekaCity;

    /** Normalized city name matching our system e.g. "Delhi NCR" */
    private String mappedCity;

    private String jobTitle;

    /** 0 = Working, 1 = Relieved */
    private Integer employmentStatus;

    /** 0 = None, 1 = Initiated, 2 = Completed */
    private Integer exitStatus;

    private Instant syncedAt;
}
