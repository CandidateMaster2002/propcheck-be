package com.propchk.be.dto;

import lombok.Data;

@Data
public class CreateBookingRequest {
    private Long leadId;
    /** INSPECTION or REINSPECTION */
    private String bookingType;
    private String city;
    /** yyyy-MM-dd */
    private String date;
    /** MORNING or EVENING */
    private String slot;
    /** e.g. "10:00", "10:30", "14:00" */
    private String slotTime;
    /** FK to User.id who is creating the booking */
    private Long createdByUserId;
}
